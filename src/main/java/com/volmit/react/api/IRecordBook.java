package com.volmit.react.api;

import primal.lang.collection.GMap;

import java.io.File;

public interface IRecordBook<T extends IRecord<?>> {
    int getSize();

    T getRecord(long record);

    long getOldestRecordTime();

    void addRecord(T t);

    long getLatestRecordTime();

    int countRecords(long from, long to);

    GMap<Long, T> getRecords(long from, long to);

    int purgeRecordsBefore(long time);

    void save();

    File getFile();
}
