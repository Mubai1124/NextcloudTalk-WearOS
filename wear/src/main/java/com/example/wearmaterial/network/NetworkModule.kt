package com.example.wearmaterial.network

import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.wearmaterial.api.NcApi
import com.google.gson.GsonBuilder

object NetworkModule {
    private var _baseUrl: String = ""
    private var _username: String = ""
    private var _password: String = ""
    private var _api: NcApi? = null
    private var isConfigured: Boolean = false
    
    // 暴露配置信息供其他组件使用
    val baseUrl: String? get() = if (isConfigured) _baseUrl else null
    val username: String? get() = if (isConfigured) _username else null
    val password: String? get() = if (isConfigured) _password else null
    
    fun configure(serverUrl: String, user: String, pass: String) {
        this._baseUrl = serverUrl.trimEnd('/')
        this._username = user
        this._password = pass
        this.isConfigured = true
        // 重新创建 API 实例
        _api = createApi()
    }
    
    fun isConfigured(): Boolean = isConfigured
    
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    private fun createApi(): NcApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(_username, _password))
                    .header("Accept", "application/json")
                    .header("OCS-APIRequest", "true")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val effectiveUrl = if (_baseUrl.isNotEmpty()) "$_baseUrl/" else "http://localhost:8082/"
        
        return Retrofit.Builder()
            .baseUrl(effectiveUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NcApi::class.java)
    }
    
    val api: NcApi
        get() {
            if (!isConfigured) {
                throw IllegalStateException("NetworkModule not configured. Call configure() first.")
            }
            return _api ?: createApi()
        }
}
