package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
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
    public BaseResult storageDeletion(@RequestBody List<StorageDeletionInfo> list) {
        log.warn("数据配额设置, 参数: params is {}", list);
        if (StringUtil.isEmpty(list)) {
            return BaseResultGenerator.failure(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.PARAMETER_NOT_OBTAINED));
        }
        for (StorageDeletionInfo info : list) {
            if (StringUtil.isBlank(info.getType()) || StringUtil.isBlank(String.valueOf(info.getMaxDays()))) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.LOGTYPE_AND_MAXDAYS_NOT_NULL));
            }
            if ("ALL".equalsIgnoreCase(info.getType()) && list.size() != 1) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.CANNOT_COEXIST_OTHER_TYPES));
            }
            if (!(info.getMaxDays() >= 0 && info.getMaxDays() <= 2000)) {
                throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.MAXDAYS_ERROR));
            }
        }
        return systemService.deleteStorage(list);
    }

    @RequestMapping("setting/storage/quota/daily_trend")
    public BaseResult dailyTrendOfStorage(@RequestParam Map<String, Object> param) {
        String searchStartTime = StringUtil.stripToEmpty((String) param.get("start_time"));
        String searchEndTime = StringUtil.stripToEmpty((String) param.get("end_time"));
        return systemService.dailyTrendOfStorage(searchStartTime, searchEndTime);
    }


    @GetMapping(value = "setting/tool/password_encryption")
    public BaseResult getCiphertext(EncryptionInfo param) {
        log.info("Plaintext encrypted,The plaintext argument is : {}", param);
        if (StringUtil.isEmpty(param.getPassword()) || StringUtil.isEmpty(param.getSalt())) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.PASSWORD_OR_SALT_CANNOT_BE_EMPTY));
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
