package org.mpisws.sddrservice.lib.threadpool;

import android.os.AsyncTask;

/**
 *
 * @author verdelyi
 */
public abstract class AsyncTaskV2<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    protected abstract void onPostExecuteV2(final Result result);

    @Override
    protected final void onPostExecute(final Result result) {
        onPostExecuteV2(result);
        AsyncTaskManager.get().unregisterAsyncTask(this);
    }
}
