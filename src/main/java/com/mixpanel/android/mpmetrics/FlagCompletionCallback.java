package com.mixpanel.android.mpmetrics;

public interface FlagCompletionCallback<T> {
  void onComplete(T result);
}
