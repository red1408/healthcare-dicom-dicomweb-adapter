package com.google.cloud.healthcare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class DicomWebValidation {
    private static Logger log = LoggerFactory.getLogger(DicomWebValidation.class);

    private static String DICOMWEB_PATH = "https:\\/\\/healthcare.googleapis.com\\/.*?\\/projects\\/.*?\\/locations\\/.*?\\/datasets\\/.*?\\/dicomStores\\/.*?\\/dicomWeb";
    private static String STUDIES = "studies";
    private static String STUDIES_PATH = DICOMWEB_PATH + "\\/" + STUDIES;
    private static String HEALTHCARE_API_ROOT = "https://healthcare.googleapis.com";

    public static ValidationPattern DICOMWEB_ROOT_VALIDATION =
            new ValidationPattern(Pattern.compile(DICOMWEB_PATH), "Google Healthcare Api dicomWeb root path");
    public static ValidationPattern DICOMWEB_STUDIES_VALIDATION =
            new ValidationPattern(Pattern.compile(STUDIES_PATH), "Google Healthcare Api dicomWeb studies path");

    public static void validatePath(String path, ValidationPattern validation){
        if(path.startsWith(HEALTHCARE_API_ROOT)) {
            if (!validation.pattern.matcher(path).matches()) {
                throw new IllegalArgumentException("Path: " + path + " is not a valid " + validation.name);
            }
        } else {
            if(validation == DICOMWEB_ROOT_VALIDATION && path.endsWith(STUDIES)){
                throw new IllegalArgumentException("Path: " + path + " is not dicomWeb root (ends with 'studies')");
            }
        }
    }

    private static class ValidationPattern{
        private Pattern pattern;
        private String name;

        private ValidationPattern(Pattern pattern, String name){
            this.pattern = pattern;
            this.name = name;
        }
    }
}
