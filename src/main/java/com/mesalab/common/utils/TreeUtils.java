package com.mesalab.common.utils;


import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public class TreeUtils {


    /**
     * 遍历树结构
     * @param root 节点树
     * @param getChildrenNode 获取当前节点的子节点列表；函数，接收当前节点对象
     * @param behavior 当前节点操作函数；定义遍历当前节点的操作行为
     * @param <T> 树的节点对象
     */
    public static <T> void traversalTree(List<T> root, Function<T, List<T>> getChildrenNode,
                                         Consumer<T> behavior) {

        Stack<T> stack = new Stack<>();
        root.forEach(stack :: push);
        while (!stack.isEmpty()) {
            T o = stack.pop();
            behavior.accept(o);
            List<T> childrens = getChildrenNode.apply(o);
            if (StringUtil.isNotEmpty(childrens)) {
                childrens.forEach(stack :: push);
            }
        }

    }

    /**
     * 平铺树结构
     * @param root 节点树
     * @param getChildrenNode 获取当前节点的子节点列表；函数，接收当前节点对象
     * @param <T> 树的节点对象
     * @return 平铺后的节点结构
     */
    public static <T> List<T> flatTree(List<T>  root,
                                       Function<T, List<T>> getChildrenNode) {
        List<T> list = new ArrayList<>();
        traversalTree(root, getChildrenNode,  list :: add);
        return list;
    }





    /**
     * 聚合为树结构
     * @param rootParentID 根节点父ID
     * @param <R, List<T>> protocolMap 协议数据偏平结构
     * @param loadKey 节点的ID；函数，接收一个节点获取唯一ID
     * @param write 节点Children写入函数；函数，接收当前节点和子节点列表，然后将子节点项写入当前节点。
     * @param <T> 当前节点对象
     * @param <R> 请求节点返回的属性信息
     * @return 树结构列表
     */
    public static <T, R> List<T> mergeTree(Map<R, List<T>> protocolMap,
                                           String rootParentID,
                                           Function<T, R> loadKey,
                                           BiConsumer<T, List<T>> write) {

        List<T> root = protocolMap.get(rootParentID);
        Stack<T> stack = new Stack<T>();
        root.forEach(stack::push);
        while (!stack.isEmpty()) {
            T o = stack.pop();
            R key = loadKey.apply(o);
            List<T> children = protocolMap.get(key);
            write.accept(o, children);
            if (StringUtil.isNotEmpty(children)) {
                children.forEach(stack :: push);
            }

        }
        return root;
    }










}
