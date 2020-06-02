# **数据平台对外统一查询网关**

## Introduction

统一查询网关-Galaxy SQL，通过Springboot 2.0技术构建，提供交互式SQL分析和例行调度窗口，支持流式、批量作业，让您更方便编写和提交ETL程序，高效执行大数据计算程序，希望让大数据处理变的更简单。特点如下：

- 基于Http Rest 访问；通过Http Rest SQL 访问方式，对外提供数据服务。
- 解耦多层数据源；通过SQL（Mysql语法）可查询 Mysql、Clickhouse及Apache Druid 等数据存储引擎。
- 基于SQL声明式编程；面向分析人员，可直接定义所需要的结果，无需关心其内部的计算过程。



## 系统架构





## SQL查询解析约束



**空白行**

语法结构之间可以有任意数量的空白字符，包括空格，缩进，换行，CR，form feed

**关键字**

类似SELECT这种的关键字是大小写不敏感的，但和标准SQL不同，其他关键字（包括列名，方法名等）都是大小写敏感的

**标识符**

不用引号的标识符首字符必须是拉丁字符或下划线，后面可以是拉丁字符，下划线或数字，即满足如下的正则表达式：

```
^[a-zA-Z_][0-9a-zA-Z_]*$
```

例如这些是合法标识符：x,1, X_y__Z123

引号中的标识符：和MySQL一样，使用id的形式，中间可以使用任意字符，特殊字符（比如`符号本身）可以使用反斜杠转义。转义规则和字符串常量相同。推荐使用没有括号的标识符。

**别名**

在SELECT请求中，表达式可以使用AS语法指定别名。表达式在AS左边，别名在AS右边。和标准SQL不同，别名不仅仅可以在表达式顶层指定，也可以用在任意的语句中。



## **安装部署**

**Release 下载** [galaxy-qgw-service_releases](https://git.mesalab.cn/galaxy/galaxy-qgw-service/wikis/Galaxy-Releases)

**基于Docker**

- 初始化部署


需要安装docker环境，依赖JDK1.8

1. 拷贝配置文件夹config/ 5个yml文件到docker 宿主机器目录 ./qgw-deploy

2. 拷贝项目Dockerfile 到宿主机器目录 ./qgw-deploy

3. 拷贝 galaxy-qgw-service-x.jar 到宿主目录 ./qgw-deploy

4. 执行./build-deploy.sh 完成部署

5. 启停/home/tsg/galaxy/galaxy-qgw-service/bin start.sh|stop.sh

6. 查看日志：tail -f /home/tsg/galaxy/galaxy-qgw-service/logs/galaxy-qgw-service.log

7. 修改配置文件 

   ```
   docker exec -u 0 -it galaxy-qgw-service /bin/bash
   #配置文件config 目录下
   ```

8. 重启 stop.sh & start.sh 

- 已有镜像部署


1. 加载备份镜像文件 docker load  < ./docker_image_galaxy-qgw-service_[version].tar

2. 修改镜像tag docker tag 79470009165e ceiec/galaxy-qgw-service:[version]

3. 部署容器 docker run -d  --name galaxy-qgw-service -v /home/tsg/galaxy/galaxy-qgw-service/logs:/home/tsg/galaxy/galaxy-qgw-service/logs -p 9999:9999 --restart=always galaxy/galaxy-qgw-service:[version]

4. 启停 docker start|stop galaxy-qgw-service

5. 查看日志tail -f /home/tsg/galaxy/galaxy-qgw-service/logs/galaxy-qgw-service.log
6. 备份镜像文件 docker save 79470009165e  > /home/tsg/galaxy/docker_image_galaxy-qgw-service_[version].tar 

- 部署前提

  1. 修改 Dockerfile

     时区默认Asia/Shanghai，例如修改+6区如下：

     ```
     ENV TZ=Asia/Almaty
     ```

  2. 配置文件修改

     修改applicationion-prod.yml

     ```
     clickhouse:
       url: jdbc:clickhouse://10.4.35.22:8123 //修改ck地址
       driverClassName: ru.yandex.clickhouse.ClickHouseDriver
       dbname: tsg_galaxy
       username: tsgreport
       password: XX //密钥
       publicKey: XX
       
       druid:
       url: 10.4.35.16:8082/druid/v2/sql //修改druid地址
       dbname: druid
       sqlTimeZone: Asia/Almaty //修改时区
     
     hbase:
       url: 10.4.35.1:4444 //修改hbase地址
       dbname: tsg 
       tableName: report_result
       columnFamily: response
       columnName: result
     ```