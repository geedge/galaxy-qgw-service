**数据平台对外统一查询网关**

**安装部署**

**基于Docker**


*  离线初始化部署
    
     需要安装docker环境，依赖JDK1.8

    1. 拷贝配置文件夹config/ 5个yml文件到docker 宿主机器目录 /root/docker/galaxy-qgw-deploy
    
    2. 拷贝项目Dockerfile 到宿主机器目录 /root/docker/galaxy-qgw-deploy
    
    3. 拷贝 galaxy-qgw-service-x.jar 到宿主目录 /root/docker/galaxy-qgw-deploy
    
    4. 构建镜像 docker build -t ceiec/galaxy-qgw-service:1.0 .
    
    5. 部署容器 docker run -d  --name galaxy-qgw-service -v /home/ceiec/galaxy-qgw-service/logs:/home/ceiec/galaxy-qgw-service/logs -p 9091:9000 ceiec/galaxy-qgw-service:1.0
    
    6. 启停 docker start|stop galaxy-qgw-service
    
    7. 查看日志：tail -f /home/ceiec/galaxy-qgw-service/logs/galaxy-qgw-service.log

*  已有镜像部署
    
    1. 加载备份镜像文件 docker load  < ./docker_image_galaxy-qgw-service_1.0.tar
    
    2. 修改镜像tag docker tag 79470009165e ceiec/galaxy-qgw-service:1.0
    
    3. 部署容器 docker run -d  --name galaxy-qgw-service -v /home/ceiec/galaxy-qgw-service/logs:/home/ceiec/galaxy-qgw-service/logs -p 9091:9000 ceiec/galaxy-qgw-service:1.0
    
    4. 启停 docker start|stop galaxy-qgw-service
    
    5. 查看日志tail -f /home/ceiec/galaxy-qgw-service/logs/galaxy-qgw-service.log
    6. 备份镜像文件 docker save 79470009165e  > /home/ceiec/docker_image_galaxy-qgw-service_1.0.tar 
    
    
    
    