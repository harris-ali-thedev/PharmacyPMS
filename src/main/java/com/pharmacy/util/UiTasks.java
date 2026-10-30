package com.pharmacy.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Runs blocking work (DB calls) on a daemon thread and delivers the result on the FX thread. */
public final class UiTasks {

    private UiTasks() {}

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "ui-task");
        thread.setDaemon(true);
        thread.start();
    }
}
