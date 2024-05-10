package com.mesalab.qgw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.QueryOption;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.DSLQueryContext;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.service.*;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * TODO
 *
 * @Classname PacketCombineDslService
 * @Date 2024/4/19 16:16
 * @Author wWei
 */
@Service
@PropertySource(value = "classpath:dsl-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class PacketCombineDslServiceImpl implements PacketCombineDslService {

    private static final String DATAPATH_TELEMETRY_RECORD = "DATAPATH_TELEMETRY_RECORD";
    private static final String X_HOS_META_FILE_TYPE_KEY = "x-hos-meta-file-type";
    private static final String X_HOS_META_FILE_TYPE_VALUE_PCAPNG = "pcapng";
    private static final String TROUBLESHOOTING_FILE_BUCKET = "troubleshooting_file_bucket";
    private static final String FILED_MEASUREMENTS = "measurements";
    private static final String FILED_PACKET = "packet";
    private static final String FILED_TIMESTAMP_US = "timestamp_us";
    private static final String FILED_PACKET_LENGTH = "packet_length";

    private static final Log log = LogFactory.get();
    private Environment environment;
    private DatabaseService databaseService;
    private SQLSyncQueryService sqlSyncQueryService;
    private HosService hosService;

    @Override
    public BaseResult run(DSLQueryRequestParam dslQueryRequestParam) {
        String sql = buildSQL(dslQueryRequestParam);
        List<Map<String, Object>> packets = getPackets(sql);
        byte[] bytes = packetCombine(packets);
        InputStream inputStream = new ByteArrayInputStream(bytes);
        return uploadFile(dslQueryRequestParam.getId(), inputStream);
    }

    @Override
    public List<Map<String, Object>> getPackets(String sql) {
        BaseResult<List<Map<String, Object>>> baseResult = sqlSyncQueryService.executeQuery(
                SQLQueryContext.builder()
                        .originalSQL(sql)
                        .option(QueryOption.REAL_TIME.getValue())
                        .build());
        if (baseResult.isSuccess()) {
            return baseResult.getData();
        }
        throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), "get packet records failed"));
    }

    @Override
    public byte[] packetCombine(List<Map<String, Object>> records) {
        List<byte[]> packetList = new ArrayList<>();
        for (Map<String, Object> map : records) {
            String measurements = JSONUtil.toJsonStr(map.get(FILED_MEASUREMENTS));
            int packetLen = Integer.parseInt(String.valueOf(map.get(FILED_PACKET_LENGTH)));
            long timestampUS = Long.parseLong(String.valueOf(map.get(FILED_TIMESTAMP_US)));
            String base64EncodedString = String.valueOf(map.get(FILED_PACKET));
            byte[] decodedBytes = Base64.getDecoder().decode(base64EncodedString);
            if (decodedBytes.length != packetLen) {
                log.warn("Packet content does not match the specified length. Actual length: {}, Specified length: {}. Packet will be discarded. Packet in Base64 format: {}", decodedBytes.length, packetLen, map.get(FILED_PACKET));
                continue;
            }
            byte[] bytes = generatePacketBlock(timestampUS, decodedBytes, packetLen, measurements);
            packetList.add(bytes);
        }
        return mergeByte(packetList);
    }

    @Override
    public BaseResult uploadFile(String fileName, InputStream inputStream) {
        Map<String, String> objectObjectHashMap = Maps.newHashMap();
        objectObjectHashMap.put(X_HOS_META_FILE_TYPE_KEY, X_HOS_META_FILE_TYPE_VALUE_PCAPNG);
        BaseResult baseResult = hosService.uploadFile(TROUBLESHOOTING_FILE_BUCKET, fileName, inputStream, objectObjectHashMap);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        return hosService.getFile(TROUBLESHOOTING_FILE_BUCKET, fileName);
    }

    private String buildSQL(DSLQueryRequestParam dslQueryRequestParam) {
        String dataSource = dslQueryRequestParam.getDataSource();
        DSLQueryContext dslQueryContext = BeanUtil.copyProperties(dslQueryRequestParam, DSLQueryContext.class);
        return dslQueryContext.toSql(
                environment.getProperty(DATAPATH_TELEMETRY_RECORD)
                , dataSource
                , databaseService.getPartitionKey(dataSource)
                , "unix_timestamp");
    }

    private static byte[] mergeByte(List<byte[]> packetList) {
        //拼接文件头信息和所有增强数据包块
        List<byte[]> pcapFileDataList = new ArrayList<>();
        //添加文件头信息
        pcapFileDataList.add(generatePCAPNGHeader());
        //添加所有增强数据包块
        pcapFileDataList.addAll(packetList);
        //合并所有数据
        byte[][] newBytes = new byte[pcapFileDataList.size()][];
        pcapFileDataList.toArray(newBytes);
        return ArrayUtil.addAll(newBytes);
    }

    private static byte[] generatePacketBlock(long timestamp, byte[] blockData, int packetLen, String measurements) {
        //计算注释的长度
        List<byte[]> commentsList = new ArrayList<>();
        int commentsLength = 0;
        //获取注释的json数组
        JSONArray measurementsJsonArray = JSONUtil.parseArray(measurements);
        for (Object measurement : measurementsJsonArray) {
            byte[] comment = ((JSONObject) measurement).toJSONString(2).getBytes();
            if (comment.length > 65535) {
                comment = "The comment exceeds the length limit of 65535 characters and cannot be displayed.".getBytes();
            }
            //获取注释长度，补齐4字节
            int commentPadding = 0;
            if (comment.length % 4 != 0) {
                commentPadding = 4 - comment.length % 4;
            }
            int commentLength = comment.length + commentPadding;
            commentsLength += commentLength + 4;
            commentsList.add(comment);
        }
        //计算数据包块的填充字节数
        int blockDataLength = blockData.length;
        int packetPadding = 0;
        if (blockDataLength % 4 != 0) {
            packetPadding = 4 - blockDataLength % 4;
        }
        //计算增强数据包块长度
        int packetBlockLength = commentsLength + blockDataLength + packetPadding + 32;
        //生成增强数据包块
        ByteBuffer packetBlockBuffer = ByteBuffer.allocate(packetBlockLength);
        packetBlockBuffer.order(ByteOrder.LITTLE_ENDIAN);
        packetBlockBuffer.putInt(0x00000006);//增强数据包块块类型
        packetBlockBuffer.putInt(packetBlockLength);//增强数据包块长度
        packetBlockBuffer.putInt(0);//接口ID，从IDB来，默认为0
        packetBlockBuffer.putInt((int) (timestamp >> 32));//时间戳高32位
        packetBlockBuffer.putInt((int) (timestamp & 0xFFFFFFFFL));//时间戳低32位
        packetBlockBuffer.putInt(blockDataLength);//捕获的数据长度
        packetBlockBuffer.putInt(packetLen);//原始数据包长度
        packetBlockBuffer.put(blockData);//数据包
        //补齐4字节
        for (int i = 0; i < packetPadding; i++) {
            packetBlockBuffer.put((byte) 0);
        }
        //注释数据
        for (byte[] comment : commentsList) {
            packetBlockBuffer.putChar((char) 1);//选项类型，注释固定为1
            packetBlockBuffer.putChar((char) comment.length);//注释长度
            packetBlockBuffer.put(comment);//注释内容
            //计算注释的填充字节数
            int commentPadding = 0;
            if (comment.length % 4 != 0) {
                commentPadding = 4 - comment.length % 4;
            }
            //补齐4字节
            for (int i = 0; i < commentPadding; i++) {
                packetBlockBuffer.put((byte) 0);
            }
        }
        packetBlockBuffer.putInt(packetBlockLength);//增强数据包块长度
        return packetBlockBuffer.array();
    }

    private static byte[] generatePCAPNGHeader() {
        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        //节标题块标识
        buffer.putInt(0x0A0D0D0A);
        //节标题块长度
        buffer.putInt(28);
        //块的数据顺序，大端或者小端模式,0x4D3C2B1A为小端，0x1A2B3C4D为大端
        buffer.putInt(0x1A2B3C4D);
        //主版本信息
        buffer.putShort((short) 1);
        //次版本信息
        buffer.putShort((short) 0);
        //下一块长度，-1表示未指定
        buffer.putLong(-1);
        //节标题块长度
        buffer.putInt(28);
        //接口描述块标识
        buffer.putInt(1);
        //接口描述块长度
        buffer.putInt(20);
        //链路类型
        buffer.putInt(1);
        //从每个数据包转储的最大字节数。每个数据包超过此值的部分将不会存储在文件中。
        buffer.putInt(0);
        //接口描述块长度
        buffer.putInt(20);
        return buffer.array();
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setSqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        this.sqlSyncQueryService = sqlSyncQueryService;
    }

    @Autowired
    public void setHosService(HosService hosService) {
        this.hosService = hosService;
    }
}
