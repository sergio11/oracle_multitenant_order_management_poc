package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.tenant.adapter.web.TenantContextFilter;
import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit servlet-filter ordering (L4/L9).
 *
 * <p>CorrelationFilter runs first (order 0) so the correlation id is in the
 * MDC before TenantContextFilter (order 10) builds error bodies. Declaring a
 * FilterRegistrationBean for each filter also suppresses Boot's default
 * (unordered) auto-registration of those @Component filters, so each filter
 * executes exactly once.
 */
@Configuration
public class FilterOrderConfig {

    @Bean
    public FilterRegistrationBean<CorrelationFilter> correlationFilterRegistration(CorrelationFilter filter) {
        FilterRegistrationBean<CorrelationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(0);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(10);
        return registration;
    }
}
