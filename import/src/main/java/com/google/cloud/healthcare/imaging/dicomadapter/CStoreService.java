// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.healthcare.imaging.dicomadapter;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.IDicomWebClient.DicomWebException;
import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.Event;
import com.google.cloud.healthcare.imaging.dicomadapter.monitoring.MonitoringService;
import com.google.common.io.CountingInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to handle server-side C-STORE DICOM requests.
 */
public class CStoreService extends BasicCStoreSCP {

  private static Logger log = LoggerFactory.getLogger(CStoreService.class);

  private final IDicomWebClient defaultDicomWebClient;
  private final Map<DestinationFilter, IDicomWebClient> destinationMap;
  private final DicomRedactor redactor;

  CStoreService(IDicomWebClient defaultDicomWebClient,
      Map<DestinationFilter, IDicomWebClient> destinationMap,
      DicomRedactor redactor) {
    this.defaultDicomWebClient = defaultDicomWebClient;
    this.destinationMap = destinationMap;
    this.redactor = redactor;
  }

  @Override
  protected void store(
      Association association,
      PresentationContext presentationContext,
      Attributes request,
      PDVInputStream inPdvStream,
      Attributes response)
      throws DicomServiceException, IOException {
    try {
      MonitoringService.addEvent(Event.CSTORE_REQUEST);

      String sopClassUID = request.getString(Tag.AffectedSOPClassUID);
      String sopInstanceUID = request.getString(Tag.AffectedSOPInstanceUID);
      String transferSyntax = presentationContext.getTransferSyntax();

      validateParam(sopClassUID, "AffectedSOPClassUID");
      validateParam(sopInstanceUID, "AffectedSOPInstanceUID");

      final CountingInputStream countingStream;
      final IDicomWebClient destinationClient;
      if(destinationMap != null && destinationMap.size() > 0){
        DicomInputStream inDicomStream  = new DicomInputStream(inPdvStream);
        inDicomStream.mark(Integer.MAX_VALUE); // do or die (OOM)
        Attributes attrs = inDicomStream.readDataset(-1, Tag.PixelData);
        inDicomStream.reset();

        countingStream = new CountingInputStream(inDicomStream);
        destinationClient = selectDestinationClient(association.getAAssociateAC().getCallingAET(), attrs);
      } else {
        countingStream = new CountingInputStream(inPdvStream);
        destinationClient = defaultDicomWebClient;
      }

      InputStream inWithHeader =
          DicomStreamUtil.dicomStreamWithFileMetaHeader(
              sopInstanceUID, sopClassUID, transferSyntax, countingStream);

      List<StreamCallable> callableList = new ArrayList<>();
      if (redactor != null) {
        callableList.add(new StreamCallable() {
          @Override
          void processStreams(InputStream inputStream, OutputStream outputStream) throws Exception {
            redactor.redact(inputStream, outputStream);
          }
        });
      }
      callableList.add(new StreamCallable() {
        @Override
        void processStreams(InputStream inputStream, OutputStream outputStream) throws Exception {
          destinationClient.stowRs(inputStream);
        }
      });

      executeStreamCallables(association.getApplicationEntity().getDevice().getExecutor(),
          inWithHeader, callableList);

      response.setInt(Tag.Status, VR.US, Status.Success);
      MonitoringService.addEvent(Event.CSTORE_BYTES, countingStream.getCount());
    } catch (DicomWebException e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw new DicomServiceException(e.getStatus(), e.getMessage(), e);
    } catch (DicomServiceException e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw e;
    } catch (Throwable e) {
      MonitoringService.addEvent(Event.CSTORE_ERROR);
      throw new DicomServiceException(Status.ProcessingFailure, e);
    }
  }

  private void validateParam(String value, String name) throws DicomServiceException {
    if (value == null || value.trim().length() == 0) {
      throw new DicomServiceException(Status.CannotUnderstand, "Mandatory tag empty: " + name);
    }
  }

  private IDicomWebClient selectDestinationClient(String callingAet, Attributes attrs){
    for(DestinationFilter filter: destinationMap.keySet()){
      if(filter.matches(callingAet, attrs)){
        return destinationMap.get(filter);
      }
    }
    return defaultDicomWebClient;
  }

  private void executeStreamCallables(Executor underlyingExecutor, InputStream inputStream,
      List<StreamCallable> callableList) throws Throwable {
    if (callableList.size() == 1) {
      StreamCallable singleCallable = callableList.get(0);
      singleCallable.setInputStream(inputStream);
      singleCallable.call();
    } else if (callableList.size() > 1) {
      PipedOutputStream pdvPipeOut = new PipedOutputStream();
      InputStream nextInputStream = new PipedInputStream(pdvPipeOut);
      for(int i=0; i < callableList.size(); i++){
        StreamCallable callable = callableList.get(i);
        callable.setInputStream(nextInputStream);

        if(i < callableList.size() - 1) {
          PipedOutputStream pipeOut = new PipedOutputStream();
          nextInputStream = new PipedInputStream(pipeOut);
          callable.setOutputStream(pipeOut);
        }
      }

      ExecutorCompletionService<Void> ecs = new ExecutorCompletionService<>(underlyingExecutor);
      for(StreamCallable callable : callableList){
        ecs.submit(callable);
      }

      try (pdvPipeOut) {
        // PDVInputStream is thread-locked
        StreamUtils.copy(inputStream, pdvPipeOut);
      } catch (IOException e) {
        // causes or is caused by exception in redactor.redact, no need to throw this up
        log.trace("Error copying inputStream to pdvPipeOut", e);
      }

      try {
        for (int i = 0; i < callableList.size(); i++) {
          ecs.take().get();
        }
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    }
  }

  abstract private static class StreamCallable implements Callable<Void> {
    private InputStream inputStream;
    private OutputStream outputStream;

    public void setInputStream(InputStream inputStream) {
      this.inputStream = inputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
      this.outputStream = outputStream;
    }

    @Override
    public Void call() throws Exception {
      try (InputStream finalInputStream = inputStream) {
        try (OutputStream finalOutputStream = outputStream) {
          processStreams(finalInputStream, finalOutputStream);
        }
      }
      return null;
    }

    abstract void processStreams(InputStream inputStream, OutputStream outputStream) throws Exception;
  }
}
