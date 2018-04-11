package json.chao.com.wanandroid.di.module;

import android.text.TextUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import json.chao.com.wanandroid.app.GeeksApp;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import json.chao.com.wanandroid.core.http.api.GeeksApis;
import json.chao.com.wanandroid.BuildConfig;
import json.chao.com.wanandroid.app.Constants;
import json.chao.com.wanandroid.core.http.cookies.CookiesManager;
import json.chao.com.wanandroid.di.qualifier.WanAndroidUrl;
import json.chao.com.wanandroid.utils.CommonUtils;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * @author quchao
 * @date 2017/11/27
 */

@Module
public class HttpModule {

    @Singleton
    @Provides
    GeeksApis provideGeeksApi(@WanAndroidUrl Retrofit retrofit) {
        return retrofit.create(GeeksApis.class);
    }

    @Singleton
    @Provides
    @WanAndroidUrl
    Retrofit provideGeeksRetrofit(Retrofit.Builder builder, OkHttpClient client) {
        return createRetrofit(builder, client, GeeksApis.HOST);
    }

    @Singleton
    @Provides
    Retrofit.Builder provideRetrofitBuilder() {
        return new Retrofit.Builder();
    }


    @Singleton
    @Provides
    OkHttpClient.Builder provideOkHttpBuilder() {
        return new OkHttpClient.Builder();
    }

    @Singleton
    @Provides
    OkHttpClient provideClient(OkHttpClient.Builder builder) {
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(loggingInterceptor);
        }
        File cacheFile = new File(Constants.PATH_CACHE);
        Cache cache = new Cache(cacheFile, 1024 * 1024 * 50);
        Interceptor cacheInterceptor = chain -> {
            Request request = chain.request();
            if (!CommonUtils.isNetworkConnected()) {
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build();
            }
            Response response = chain.proceed(request);
            if (CommonUtils.isNetworkConnected()) {
                int maxAge = 0;
                // 有网络时, 不缓存, 最大保存时长为0
                response.newBuilder()
                        .header("Cache-Control", "public, max-age=" + maxAge)
                        .removeHeader("Pragma")
                        .build();
            } else {
                // 无网络时，设置超时为4周
                int maxStale = 60 * 60 * 24 * 28;
                response.newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .removeHeader("Pragma")
                        .build();
            }
            return response;
        };
        //设置缓存
        builder.addNetworkInterceptor(cacheInterceptor);
        builder.addInterceptor(cacheInterceptor);
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            String requestUrl = request.url().toString();
            String domain = request.url().host();
            // set-cookie maybe has multi, login to save cookie
            boolean isLoginOrRegister = requestUrl.contains(Constants.SAVE_USER_LOGIN_KEY)
                    || requestUrl.contains(Constants.SAVE_USER_REGISTER_KEY);
            if (isLoginOrRegister && !response.headers(Constants.SET_COOKIE_KEY).isEmpty()) {
                List<String> cookies = response.headers(Constants.SET_COOKIE_KEY);
                String cookie = CommonUtils.encodeCookie(cookies);
                GeeksApp.getAppComponent().preferencesHelper().setCookie(domain, cookie);
            }
            return response;
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Request.Builder builder1 = request.newBuilder();
            String domain = request.url().host();
            if (TextUtils.isEmpty(domain)) {
                return chain.proceed(request);
            }
            String cookie = GeeksApp.getAppComponent().preferencesHelper().getCookie(domain);
            if (TextUtils.isEmpty(cookie)) {
                return chain.proceed(request);
            }
            builder1.addHeader(Constants.COOKIE, cookie);
            return chain.proceed(builder1.build());
        });
        builder.cache(cache);
        //设置超时
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.readTimeout(20, TimeUnit.SECONDS);
        builder.writeTimeout(20, TimeUnit.SECONDS);
        //错误重连
        builder.retryOnConnectionFailure(true);
        //cookie认证
        builder.cookieJar(new CookiesManager());
        return builder.build();
    }

    private Retrofit createRetrofit(Retrofit.Builder builder, OkHttpClient client, String url) {
        return builder
                .baseUrl(url)
                .client(client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}
