ARG JDK_IMAGE
ARG GOLANG_IMAGE
#编译环境，生成二进制文件
FROM ${GOLANG_IMAGE} as builder
WORKDIR /build
COPY xjar.go /build/
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o xjar .

FROM ${JDK_IMAGE}
MAINTAINER Galaxy
VOLUME /tmp
WORKDIR /home/tsg/galaxy/galaxy-qgw-service
COPY --from=builder /build .
COPY config config
COPY dat dat
COPY docs docs
ARG JAR_FILE
COPY ${JAR_FILE} service.xjar
#dockercompose set JAVA_OPTS
ENV JAVA_OPTS=" -Xms1024m -Xmx2048m "
ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_ALL=en_US.UTF-8
#ENV TZ=Asia/Almaty
#RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
ENTRYPOINT [ "sh", "-c", "./xjar java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar galaxy-qgw-service.xjar" ]