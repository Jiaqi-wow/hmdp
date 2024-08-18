package com.hmdp.utils;

public interface ILock {
    boolean isLock(long timeoutSec);
    void unlock();
}
