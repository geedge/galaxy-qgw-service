package com.mesalab.knowledge.entity;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mesalab.knowledge.parse.MatchEnum;
import com.mesalab.knowledge.parse.SymbolEnum;

import lombok.Data;

import static java.util.stream.Collectors.joining;

/**
 * @author Administrator
 */
@Data
public class QueryExp {

	protected String type;
	protected String filedKey;
	protected List<Object> filedValues;

	protected String operator;

	protected String qlSegment;

	/**
	 * @return java.lang.String
	 * @Author zhq
	 * @Description //TODO 根据type获取符合创建ql片段
	 * @Date 2020\7\5 0005
	 * @Param [queryExp]
	 **/
	@Deprecated
	public String joinQueryExp() {
		if (this instanceof Match) {
			MatchEnum match = MatchEnum.match(type, null);
			Objects.requireNonNull(match, "match type error");
			return Joiner.on(" or ").join(filedKey, "like", MessageFormat.format(match.matchExp(), filedValues));
		} else if (this instanceof Range) {
			SymbolEnum range = SymbolEnum.match(type, null);
			Objects.requireNonNull(range, "range type error");
			return Joiner.on(" ").join(filedKey, range.symbol(), filedValues);
		}
		return "";
	}

}
