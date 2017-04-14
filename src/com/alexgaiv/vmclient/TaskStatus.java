package com.alexgaiv.vmclient;

enum TaskStatus
{
    FAILED(0, "Failed"),
    COMPLETED(1, "Completed"),
    WAITING(2, "Waiting"),
    RUNNING(3, "Running");

    TaskStatus(int code, String statusString) {
        this.code = code;
        this.statusString = statusString;
    }
    public int getCode() { return code; }
    public String toString() { return statusString; }

    private int code;
    private String statusString;
}
