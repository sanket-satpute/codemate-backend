//package com.codescope.backend.service;
//
//import java.lang.reflect.Field;
//
//public class TestUtils {
//    public static void setField(Object target, String fieldName, Object value) {
//        try {
//            Field f = target.getClass().getDeclaredField(fieldName);
//            f.setAccessible(true);
//            f.set(target, value);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
