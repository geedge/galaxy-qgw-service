FROM jdk1.8
MAINTAINER ceiec
VOLUME /tmp
RUN mkdir -p /home/ceiec/galaxy-qgw-service/config
COPY galaxy-qgw-service-1.0.jar /home/ceiec/galaxy-qgw-service/galaxy-qgw-service.jar
ADD  config/ /home/ceiec/galaxy-qgw-service/config
WORKDIR /home/ceiec/galaxy-qgw-service
ENV JAVA_OPTS=" -Xms512m -Xmx2048m "
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar galaxy-qgw-service.jar" ]