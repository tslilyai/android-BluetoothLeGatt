package org.mpi_sws.sddr_service.lib.threadpool;

import android.os.AsyncTask;
import android.util.Log;

import org.mpi_sws.sddr_service.lib.Utils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 *
 * @author verdelyi
 */
public class AsyncTaskManager {

    private static final String TAG = Utils.getTAG(AsyncTaskManager.class);
    private static final AsyncTaskManager instance = new AsyncTaskManager();
    private final List<AsyncTask<?, ?, ?>> asyncTasks = new LinkedList<AsyncTask<?, ?, ?>>();
    
    public static final Executor rssiThreadPoolExecutor = Executors.newCachedThreadPool();
    
    private enum TaskAction {
        Register, Unregister, Cancel
    }

    private AsyncTaskManager() {
    }

    private void inspect(final TaskAction action, final AsyncTask<?, ?, ?> task) {
        Log.d(TAG, "[Active tasks: " + asyncTasks.size() + "][" + action + "] task type " + task.getClass().getSimpleName());
    }

    public static AsyncTaskManager get() {
        return instance;
    }
    
    public synchronized void submitTask(final AsyncTask<?, ?, ?> task) {
        submitTask(task, AsyncTask.THREAD_POOL_EXECUTOR);
    }
    
    public synchronized void submitTask(final AsyncTask<?, ?, ?> task, final Executor executor) {
        inspect(TaskAction.Register, task);
        asyncTasks.add(task);
        task.executeOnExecutor(executor);
    }

    public synchronized void unregisterAsyncTask(final AsyncTask<?, ?, ?> task) {
        inspect(TaskAction.Unregister, task);
        asyncTasks.remove(task);
    }

    public synchronized void cancelAllTasks() {
        Log.d(AsyncTaskManager.class.getSimpleName(), "Cancelling all AsyncTasks");
        for (AsyncTask<?, ?, ?> task : asyncTasks) {
            inspect(TaskAction.Cancel, task);
            task.cancel(true);
        }
        asyncTasks.clear();
    }
}
