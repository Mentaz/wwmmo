package au.com.codeka.warworlds.api;

import com.google.protobuf.Message;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.constraints.NotNull;

import au.com.codeka.common.Log;

/**
 * This is the main "client" that accesses the War Worlds API.
 */
@Deprecated
public class ApiClient {
  private static final Log log = new Log("ApiClient");

  /**
   * Fetches a simple string from the given URL.
   */
  public static String getString(String url) throws ApiException {
    final RequestFuture<String> future = new RequestFuture<>();
    RequestManager.i.sendRequest(
        new ApiRequest.Builder(url, "GET").completeCallback(new ApiRequest.CompleteCallback() {
          @Override
          public void onRequestComplete(ApiRequest request) {
            future.set(request.bodyString());
          }
        })
        .completeOnAnyThread(true)
        .build());

    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ApiException(e);
    }
  }

  /**
   * Fetches a raw protocol buffer from the given URL via a HTTP GET.
   *
   * \param url The URL of the object to fetch, relative to the server root (so for
   *        example, it might be "/motd" and depending on the other properties set up
   *        in the \c ApiClient, this could resolve to something like
   *        "https://warworldsmmo.appspot.com/api/v1/motd"
   * \param protoBuffFactory the class that we want to fetch, this will also determine
   *        the return value of this method.
   */
  public static <T extends Message> T getProtoBuf(String url, final Class<T> protoBuffFactory)
      throws ApiException {
    final RequestFuture<T> future = new RequestFuture<>();
    RequestManager.i.sendRequest(new ApiRequest.Builder(url, "GET")
        .completeCallback(new ApiRequest.CompleteCallback() {
          @Override
          public void onRequestComplete(ApiRequest request) {
            future.set(request.body(protoBuffFactory));
          }
        })
        .completeOnAnyThread(true)
        .build());

    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ApiException(e);
    }
  }

  /**
   * Uses the "PUT" HTTP method to put a protocol buffer at the given URL. This is useful when
   * you don't expect a response (other than "201", success)
   */
  public static boolean putProtoBuf(String url, Message pb) throws ApiException {
    return putOrPostProtoBuf("PUT", url, pb);
  }

  /**
   * Uses the "POST" HTTP method to post a protocol buffer at the given URL. This is useful when
   * you don't expect a response (other than "200", success)
   */
  public static boolean postProtoBuf(String url, Message pb) throws ApiException {
    return putOrPostProtoBuf("POST", url, pb);
  }

  /**
   * Uses the "PUT" or "POST" HTTP method to put or post a protocol buffer at the given URL.
   * This is useful when you don't expect a response (other than "2xx", success)
   */
  private static boolean putOrPostProtoBuf(String method, String url, Message pb)
      throws ApiException {

    RequestManager.i.sendRequest(new ApiRequest.Builder(url, method)
        .body(pb)
        .build());
    return true;
  }

  /**
   * Uses the "PUT" HTTP method to put a protocol buffer at the given URL.
   */
  public static <T extends Message> T putProtoBuf(String url, Message pb, Class<T> protoBuffFactory)
      throws ApiException {
    return putOrPostProtoBuff("PUT", url, pb, protoBuffFactory);
  }

  /**
   * Uses the "POST" HTTP method to post a protocol buffer at the given URL.
   */
  public static <T extends Message> T postProtoBuf(String url, Message pb,
      Class<T> protoBuffFactory) throws ApiException {
    return putOrPostProtoBuff("POST", url, pb, protoBuffFactory);
  }

  private static <T extends Message> T putOrPostProtoBuff(String method, String url, Message pb,
      final Class<T> protoBuffFactory) throws ApiException {

    final RequestFuture<T> future = new RequestFuture<>();
    RequestManager.i.sendRequest(new ApiRequest.Builder(url, method)
        .body(pb)
        .completeCallback(new ApiRequest.CompleteCallback() {
          @Override
          public void onRequestComplete(ApiRequest request) {
            future.set(request.body(protoBuffFactory));
          }
        })
        .completeOnAnyThread(true)
        .build());

    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ApiException(e);
    }
  }

  /**
   * Sends a HTTP 'DELETE' to the given URL.
   */
  public static void delete(String url) throws ApiException {
    RequestManager.i.sendRequest(new ApiRequest.Builder(url, "DELETE").build());
  }

  private static class RequestFuture<T> implements Future<T> {
    private final Object lock = new Object();
    private boolean finished;
    private T protoBuff;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return false;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      synchronized (lock) {
        while (!finished) {
          lock.wait();
        }
      }
      return protoBuff;
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    public void set(T protoBuff) {
      synchronized (lock) {
        this.protoBuff = protoBuff;
        finished = true;
        lock.notifyAll();
      }
    }
  }
}