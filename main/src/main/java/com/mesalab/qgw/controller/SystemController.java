package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.clearspring.analytics.util.Lists;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.model.job.StorageDeletionInfo;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.services.configuration.ThreadPoolMonitor;
import com.geedgenetworks.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/v1/admin")
public class SystemController {
    private static final Log log = LogFactory.get();
    @Autowired
    private SystemService systemService;
    @Autowired
    ThreadPoolMonitor threadPoolMonitor;


    @GetMapping ("setting/storage/quota")
    public BaseResult storageQuota() {
        return systemService.getStorageQuota();
    }

    @PutMapping(value = "setting/storage/quota")
    public BaseResult storageSetting(@RequestBody List<StorageDeletionInfo> list) {
        log.warn("数据配额设置, 参数: params is {}", list);
        if (StringUtil.isEmpty(list)) {
            return BaseResultGenerator.failure(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),QGWMessageConst.PARAMETER_NOT_OBTAINED));
        }
        for (StorageDeletionInfo info : list) {
            if (StringUtil.isBlank(info.getType()) || StringUtil.isBlank(String.valueOf(info.getMaxDays()))) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),QGWMessageConst.LOGTYPE_AND_MAXDAYS_NOT_NULL));
            }
            if ("ALL".equalsIgnoreCase(info.getType()) && list.size() != 1) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),QGWMessageConst.CANNOT_COEXIST_OTHER_TYPES));
            }
            if (!(info.getMaxDays() >= 0 && info.getMaxDays() <= 2000)) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),QGWMessageConst.MAXDAYS_ERROR));
            }
        }
        return systemService.deleteStorage(list);
    }

    @RequestMapping(value = "setting/storage/quota",method = RequestMethod.DELETE)
    public BaseResult storageDeletion(@RequestParam String type){
        List<StorageDeletionInfo> deletionInfoList = Lists.newArrayList();
        StorageDeletionInfo deletionInfo = new StorageDeletionInfo();
        deletionInfo.setType(type);
        deletionInfo.setMaxDays(0);
        deletionInfoList.add(deletionInfo);
        return systemService.deleteStorage(deletionInfoList);
    }

    @RequestMapping("setting/storage/quota/daily_trend")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
        String searchStartTime = StringUtil.stripToEmpty((String) param.get("start_time"));
        String searchEndTime = StringUtil.stripToEmpty((String) param.get("end_time"));
        return systemService.dailyTrendOfStorage(searchStartTime, searchEndTime);
    }


    @PostMapping(value = "setting/tool/password_encryption")
    public BaseResult getCiphertext(EncryptionInfo param) {
        log.info("Plaintext encrypted,The plaintext argument is : {}", param);
        if (StringUtil.isEmpty(param.getPassword()) || StringUtil.isEmpty(param.getSalt())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(),QGWMessageConst.PASSWORD_OR_SALT_CANNOT_BE_EMPTY));
        }
        return systemService.getCiphertext(param);
    }

    @GetMapping(value = "/clear/realJobQueue")
    public BaseResult clearTaskQueue() {
        log.info("Clear real job queue.");
        threadPoolMonitor.clearQueue();
        return BaseResultGenerator.success();
    }
}
