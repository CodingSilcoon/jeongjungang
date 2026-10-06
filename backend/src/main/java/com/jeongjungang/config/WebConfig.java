package com.jeongjungang.config;

import com.jeongjungang.auth.CallerArgumentResolver;
import com.jeongjungang.ratelimit.RateLimitInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CallerArgumentResolver callerResolver;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(CallerArgumentResolver callerResolver, RateLimitInterceptor rateLimitInterceptor) {
        this.callerResolver = callerResolver;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/v1/**");
    }
}
