package com.mesalab.qgw.service;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.protocol.ProtocolTree;
import com.mesalab.common.utils.TreeUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@EnableAutoConfiguration
public class ProtocolTreeTest extends GalaxyQGWApplicationTests {

    @Test
    public void testTreeOutput() {


        String jsonString = "{\"common_schema_type\":\"BASE\",\"proto_label\":\"UDP/IPv4/MPLS/MAC\",\"common_server_ip\":\"2.78.63.111\",\"common_client_ip\":\"10.7.85.134\",\"common_server_port\":2152,\"common_client_port\":3692,\"common_stream_dir\":1,\"common_address_type\":4,\"common_s2c_pkt_num\":0,\"common_s2c_byte_num\":0,\"common_c2s_pkt_num\":8,\"common_c2s_byte_num\":1747,\"common_start_time\":1592564648,\"common_end_time\":1592564648,\"common_con_duration_ms\":0,\"common_stream_trace_id\":1929322430467,\"common_l4_protocol\":\"IPv4_UDP\",\"common_address_list\":\"3692-2152-10.7.85.134-2.78.63.111\",\"common_sled_ip\":\"127.0.0.1\",\"common_device_id\":\"CBT2201925000001\",\"common_policy_id\":0,\"common_service\":2,\"common_action\":1}";

        List<ProtocolTree> nodes = parserRawData(jsonString);
        Iterable<ProtocolTree> concatResult =  Iterables.concat(nodes);

        jsonString = "{\"common_schema_type\":\"BASE\",\"proto_label\":\"UDP/IPv4/MPLS/MAC\",\"common_server_ip\":\"2.78.63.111\",\"common_client_ip\":\"10.7.85.9\",\"common_server_port\":2152,\"common_client_port\":2152,\"common_stream_dir\":2,\"common_address_type\":4,\"common_s2c_pkt_num\":11,\"common_s2c_byte_num\":9604,\"common_c2s_pkt_num\":0,\"common_c2s_byte_num\":0,\"common_start_time\":1592564648,\"common_end_time\":1592564648,\"common_con_duration_ms\":0,\"common_stream_trace_id\":1929322430466,\"common_l4_protocol\":\"IPv4_UDP\",\"common_address_list\":\"2152-2152-10.7.85.9-2.78.63.111\",\"common_sled_ip\":\"127.0.0.1\",\"common_device_id\":\"CBT2201925000001\",\"common_policy_id\":0,\"common_service\":2,\"common_action\":1}";
        nodes = parserRawData(jsonString);

        concatResult =  Iterables.concat(concatResult, nodes);

        jsonString = "{\"common_schema_type\":\"SSL\",\"ssl_sni\":\"gs-loc.apple.com\",\"ssl_version\":\"v3\",\"ssl_cn\":\"gs-loc.apple.com\",\"ssl_san\":\"gs-loc.apple.com\",\"proto_label\":\"SSL/TCP/IPv4/GTP/UDP/IPv4/MPLS/MAC\",\"common_server_ip\":\"17.134.127.250\",\"common_client_ip\":\"100.83.154.231\",\"common_server_port\":443,\"common_client_port\":55357,\"common_stream_dir\":3,\"common_address_type\":4,\"common_s2c_pkt_num\":26,\"common_s2c_byte_num\":19998,\"common_c2s_pkt_num\":22,\"common_c2s_byte_num\":1857,\"common_start_time\":1592563211,\"common_end_time\":1592563211,\"common_con_duration_ms\":0,\"common_stream_trace_id\":1929275342849,\"common_l4_protocol\":\"IPv4_TCP\",\"common_address_list\":\"IPv4_TCP<55357-443-100.83.154.231-17.134.127.250>|GTP<525653173-38939>|IPv4_UDP<0-0-0.0.0.0-0.0.0.0>|MPLS<NULL>|MAC<000000000435-18100400013f>\",\"common_establish_latency_ms\":0,\"common_sled_ip\":\"127.0.0.1\",\"common_device_id\":\"CBT2201925000001\",\"common_policy_id\":0,\"common_service\":2,\"common_action\":1}";
        nodes = parserRawData(jsonString);
        concatResult =  Iterables.concat(concatResult, nodes);




        List<ProtocolTree> listNodes = Lists.newArrayList(concatResult);


        ProtocolTree root = new ProtocolTree("Protocols", "Protocols", null);
        List<ProtocolTree> roots = listNodes.stream().filter(o -> StringUtil.isBlank(o.getParentId())).collect(Collectors.toList());
        roots.forEach(item -> {
            root.setSentBytes(root.getSentBytes() + item.getSentBytes());
            root.setReceivedBytes(root.getReceivedBytes() + item.getReceivedBytes());
        });

        listNodes.forEach(item -> {
            item.setId("Protocols/" + item.getId());
        });

        listNodes.add(root);



        //List To Map ,相同指标数据计算
        Map<String, List<ProtocolTree>> resultMap = listNodes.stream().collect(Collectors.groupingBy(ProtocolTree::getId));

        Map<String, Object> mergeResultMap =
                Maps.transformValues(resultMap, new com.google.common.base.Function<List<ProtocolTree>, Object>() {
                    @Nullable
                    @Override
                    public Object apply(@Nullable List<ProtocolTree> input) {
                        if (StringUtil.isEmpty(input)) {
                            return null;
                        }
                        if (input.size() == 1) {
                            return input.get(0);
                        }

                        ProtocolTree firstProtocolTree = input.get(0);
                        ProtocolTree mergeProtocolTree = new ProtocolTree(firstProtocolTree.getId(), firstProtocolTree.getName(),
                                firstProtocolTree.getParentId(), 0, 0 );
                        mergeProtocolTree.setMetrics(Maps.newHashMap());

                        for (ProtocolTree protocolTree : input) {
                            mergeProtocolTree.setSentBytes(mergeProtocolTree.getSentBytes() + protocolTree.getSentBytes());
                            mergeProtocolTree.setReceivedBytes(mergeProtocolTree.getReceivedBytes() + protocolTree.getReceivedBytes());
                            for (String key : protocolTree.getMetrics().keySet()) {
                                if (StringUtil.isEmpty(mergeProtocolTree.getMetrics().get(key))) {
                                    mergeProtocolTree.getMetrics().put(key, protocolTree.getMetrics().get(key));
                                } else {
                                    long sumValue = Long.valueOf(mergeProtocolTree.getMetrics().get(key).toString())
                                            +  Long.valueOf(protocolTree.getMetrics().get(key).toString());
                                    mergeProtocolTree.getMetrics().put(key, sumValue);
                                }
                            }

                        }

                        return mergeProtocolTree;
                    }
                });

        List<ProtocolTree> mergeNodes = new ArrayList(mergeResultMap.values());




        List<ProtocolTree> treeNodes = TreeUtils.mergeTree(mergeNodes, ProtocolTree::getId, ProtocolTree::getParentId, ProtocolTree::setChildrens);
        System.out.println(JsonMapper.toJsonString(treeNodes));




    }


    /**
     * 模拟解析原始流量数据
     * @param jsonString
     * @return
     */
    private List<ProtocolTree> parserRawData(String jsonString) {

        List<ProtocolTree> lists  = Lists.newArrayList();

        Map<String, Object> results = (Map<String, Object>) JsonMapper.fromJsonString(jsonString, Map.class);

         List<String> protocolList = Lists.newArrayList(results.get("proto_label").toString().split("/"));

        Collections.reverse(protocolList);

        long sentBytes = Long.valueOf(results.get("common_c2s_byte_num").toString());
        long receivedBytes = Long.valueOf(results.get("common_s2c_byte_num").toString());
        long sentPackets = Long.valueOf(results.get("common_c2s_pkt_num").toString());
        long receivedPackets = Long.valueOf(results.get("common_s2c_pkt_num").toString());

        String key = null;

        for(String item : protocolList) {
            key = Joiner.on("/").skipNulls().join(key, item);
            ProtocolTree protocolTree = new ProtocolTree(key, item, null);
            protocolTree.setSentBytes(sentBytes);
            protocolTree.setReceivedBytes(receivedBytes);
            protocolTree.getMetrics().put("sentPackets", sentPackets);
            protocolTree.getMetrics().put("receivedPackets", receivedPackets);
            lists.add(protocolTree);

        }


        return lists;

    }
}
