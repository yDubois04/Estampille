package istic.projet.estampille.utils;

import android.util.Log;

import java.io.IOException;

import istic.projet.estampille.models.APIInfosTransformateur;
import istic.projet.estampille.models.APITransformateur;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Service using Retrofit to calls the remote API.
 */
public interface APIService {
    String TAG = APIService.class.getName();

    OkHttpClient okHttpClient = new OkHttpClient.Builder()
//            .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .addInterceptor(new LoggingInterceptor())
            .build();

    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("http://192.168.1.10:8080/") // TODO: If tests are done on a device and as long as the API run on a localhost:
            // TODO: put the IP of the machine on which the API is running. The device and the computer must be connected to the same network in order for the device to be able to call the API.
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build();

    @GET("transformateur")
    Call<APITransformateur> getTansformateur(@Query("estampille") String estampille);

    @GET("/infoTransformateur/transformateur/{id}")
    Call<APIInfosTransformateur> getInfosTansformateur(@Path("id") String estampille);

    /**
     * Custom Interceptor.
     */
    class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            long t1 = System.nanoTime();
            Log.wtf(TAG, String.format("--> Sending request %s on %s%n%s", request.url(), chain.connection(), request.headers()));
            okhttp3.Response response = chain.proceed(request);
            long t2 = System.nanoTime();
            Log.wtf(TAG, String.format("<-- Received response for %s in %.1fms%n%s", response.request().url(), (t2 - t1) / 1e6d, response.headers()));
            assert response.body() != null;
            MediaType contentType = response.body().contentType();
            String content = response.body().string();
            ResponseBody wrappedBody = ResponseBody.create(contentType, content);
            return response.newBuilder().body(wrappedBody).build();
        }
    }
}
