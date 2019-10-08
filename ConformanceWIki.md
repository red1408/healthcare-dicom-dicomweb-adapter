# DICOM Import Adapter - DICOM conformance statement

The Import Adapter converts incoming DIMSE requests to corresponding DICOMWeb requests and passes the converted results back to the DIMSE client. The following requests are supported:
- C-STORE to STOW-RS
- C-FIND to QIDO-RS
- C-MOVE uses QIDO-RS to determine which instances to transfer, then for each instance executes a 
WADO-RS request to fetch the instance and a C-STORE request to transfer it to the C-MOVE destination
- Storage commitment service to QIDO-RS

# C-FIND service

DICOM Import Adapter will query the configured DICOMWeb address via QIDO-RS and return the results.

C-FIND query on the ModalitiesInStudy tag will result in 1 QIDO-RS query per modality (sending multiple values in ModalitiesInStudy tag is not part of standard, but commonly used by DIMSE clients).
Multiple values in other tags are not supported (UID lists should be valid, but are currently not supported by healthcare api).

Supported search parameters: https://cloud.google.com/healthcare/docs/dicom#search_parameters .

Accepted presentation contexts:

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|Study Root Q/R Information Model - FIND: 1.2.840.10008.5.1.4.1.2.2.1|Implicit VR Little Endian: 1.2.840.10008.1.2| SCP
|Study Root Q/R Information Model - FIND: 1.2.840.10008.5.1.4.1.2.2.1|Explicit VR Little Endian: 1.2.840.10008.1.2.1| SCP

# C-MOVE service

Once received a Retrieve (Move) request, DICOM Import Adapter AE (or separate AE if so configured) will initiate a new association and send the requested instances via C-STORE to the Move Destination AE.

C-MOVE response(s) will be sent over the same Association used to send the C-FIND-Request. Pending responses with updates on successes/warnings/failures will be sent after each instance transfer.

DICOM Import Adapter AE will send the requested SOP Instances to the C-MOVE Destination  over newly created Associations (one per instance). C-MOVE destination AET must be configured via AETs dictionary.

Accepted presentation contexts:

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|Study Root Q/R Information Model - MOVE: 1.2.840.10008.5.1.4.1.2.2.2|Implicit VR Little Endian: 1.2.840.10008.1.2| SCP
|Study Root Q/R Information Model - MOVE: 1.2.840.10008.5.1.4.1.2.2.2|Explicit VR Little Endian: 1.2.840.10008.1.2.1| SCP

Proposed presentation contexts (C-STORE within C-MOVE delivery):

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|* (as contained in instance info json returned by QIDO-RS)|*(as contained in instance data stream returned by WADO-RS, should be Explicit VR Little Endian)| SCU

# Storage commitment service

DICOM Import Adapter will query the configured DICOMWeb address via QIDO-RS and initiate separate association to deliver N-EVENT-REPORT to caller AET.

N-ACTION response for Commitment request will be delivered on same association.

N-EVENT-REPORT with results will be delivered on a separate association (caller AET must be present in AETs dictionary).

Accepted presentation contexts:

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|Storage Commitment Push Model SOP Class: 1.2.840.10008.1.20.1|Implicit VR Little Endian: 1.2.840.10008.1.2| SCP
|Storage Commitment Push Model SOP Class: 1.2.840.10008.1.20.1|Explicit VR Little Endian: 1.2.840.10008.1.2.1| SCP

Proposed presentation contexts:

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|Storage Commitment Push Model SOP Class: 1.2.840.10008.1.20.1|Explicit VR Little Endian: 1.2.840.10008.1.2.1| SCP

# C-STORE service

DICOM Instances received in a Storage Request are passed on to configured DICOMWeb address in a STOW-RS request.

Accepted presentation contexts (any that do not match the other services):

| Abstract Syntax | Transfer Syntax | Role |
| --- | --- | --- |
|*(any SOP class)|Implicit VR Little Endian: 1.2.840.10008.1.2| SCP
|*(any SOP class)|Explicit VR Little Endian: 1.2.840.10008.1.2.1| SCP
