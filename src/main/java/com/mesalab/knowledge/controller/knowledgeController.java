package com.mesalab.knowledge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mesalab.knowledge.entity.DSLObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/")
public class knowledgeController {

    @GetMapping("/")
    public String info(){
        return "You Konow, For Graph Search!";
    }

    @PostMapping("/iplearning")
    public String ipLearning(DSLObject dSLObject){


        return "";
    }
}
