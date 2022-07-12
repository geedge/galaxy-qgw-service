package com.mesalab;

import com.mesalab.qgw.interceptor.QuerySubmitInterceptor;
import com.zdjizhi.utils.StringUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.TimeZone;

/**
 * spring-boot-seed启动主程序
 * 使用@SpringBootApplication注解最重要的功能有@Configuration、@EnableAutoConfiguration、@ComponentScan三个注解功能
 * 使用@Configuration注解可以将类中的方法作为一个bean注入到Ioc容器中，方法名就是这个bean的name，返回值为这个bean的class，方法需要使用@Bean注解
 * 使用@ComponentScan注解相当于使用<context:component-scan base-package=""/>包扫描，会自动扫描当前类所在的包，因此Application启动类一般放在root package下
 * 使用@EnableAutoConfiguration注解是借助@Import将所有符合自动配置条件bean定义加载到Ioc容器中
 * 使用@MapperScan注解是自动扫描注入mapper接口，如果不使用这个接口，可以在每个Mapper接口上增加@Mapper注解让springboot自动注入接口
 * 使用@EnableCaching注解开启缓存功能
 *
 * @author darnell
 * @date 2019/06/10
 */
@SpringBootApplication
public class GalaxyQGWApplication implements WebMvcConfigurer {

    private static final String TIMEZONE = "UTC";

    public static void main(String[] args) {
        init();
        SpringApplication.run(GalaxyQGWApplication.class, args);
    }

    public static void init() {
        System.setProperty("jasypt.encryptor.password", "galaxy");
        System.setProperty("JM.SNAPSHOT.PATH", "config");
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> inputArguments = runtimeMxBean.getInputArguments();
        if (inputArguments.stream().noneMatch(o -> o.contains("user.timezone"))
                && StringUtil.isEmpty(System.getenv().get("TZ"))) {
            // java.util.Date
            TimeZone.setDefault(TimeZone.getTimeZone(TIMEZONE));
            // joda.time.DateTime
            System.setProperty("user.timezone", TIMEZONE);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new QuerySubmitInterceptor()).addPathPatterns("/**");
    }

    @Bean
    public ServletWebServerFactory webServerFactory() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers(connector -> connector.setProperty("relaxedPathChars", "\"<>[\\]^`{|}"));
        return factory;
    }

}
