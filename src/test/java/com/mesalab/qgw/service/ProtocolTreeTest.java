package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.network.model.protocol.ProtocolTree;
import com.mesalab.common.utils.TreeUtils;
import com.zdjizhi.utils.StringUtil;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@EnableAutoConfiguration
public class ProtocolTreeTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    @Test
    public void testTreeOutput() {
        log.info("This is protocol tree parse test");
    }

}
