FROM jdk1.8
MAINTAINER galaxy
VOLUME /tmp
RUN mkdir -p /home/tsg/galaxy/galaxy-qgw-service/config
COPY galaxy-qgw-service-3.0.jar /home/tsg/galaxy/galaxy-qgw-service/galaxy-qgw-service.jar
COPY start_jar.sh /home/tsg/galaxy/galaxy-qgw-service/start_jar.sh
ADD  config/ /home/tsg/galaxy/galaxy-qgw-service/config
WORKDIR /home/tsg/galaxy/galaxy-qgw-service
ENV JAVA_OPTS=" -Xms1024m -Xmx2048m "
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
#RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
#ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar galaxy-qgw-service.jar" ]
CMD ["/home/tsg/galaxy/galaxy-qgw-service/start_jar.sh"]