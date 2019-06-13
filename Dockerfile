FROM java:8
MAINTAINER ceiec
VOLUME /tmp
WORKDIR /Users/darnell/galaxy-qgw-service
RUN mkdir -p /home/ceiec/galaxy-qgw-service/config
WORKDIR /Users/darnell/IdeaProjects/galaxy-qgw-service/target
COPY galaxy-qgw-service-1.0.jar  /home/ceiec/galaxy-qgw-service/galaxy-qgw-service.jar
ADD /Users/darnell/IdeaProjects/galaxy-qgw-service/docker/config /home/ceiec/galaxy-qgw-service/config
WORKDIR /home/ceiec/galaxy-qgw-service
ENV JAVA_OPTS=" -Xms512m -Xmx1024m "
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar galaxy-qgw-service.jar" ]
