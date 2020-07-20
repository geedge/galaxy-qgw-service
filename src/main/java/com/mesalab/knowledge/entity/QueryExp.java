package com.mesalab.knowledge.entity;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.knowledge.enums.SymbolEnum;

import lombok.Data;

import static java.util.stream.Collectors.joining;

/**
 * @author Administrator
 */
@Data
public class QueryExp {

	protected String type;
	protected String fieldKey;
	protected List<Object> fieldValues;

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
			return Joiner.on(" or ").join(fieldKey, "like", MessageFormat.format(match.matchExp(), fieldValues));
		} else if (this instanceof Range) {
			SymbolEnum range = SymbolEnum.match(type, null);
			Objects.requireNonNull(range, "range type error");
			return Joiner.on(" ").join(fieldKey, range.symbol(), fieldValues);
		}
		return "";
	}

}
