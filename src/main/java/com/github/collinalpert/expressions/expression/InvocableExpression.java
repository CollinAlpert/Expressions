/*
 * Copyright TrigerSoft <kostat@trigersoft.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.collinalpert.expressions.expression;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.List;

/**
 * Provides the base class from which the expression that represent invocable operations are derived.
 */
@EqualsAndHashCode(callSuper = true)
public abstract class InvocableExpression extends Expression {

	private final List<ParameterExpression> parameters;

	protected InvocableExpression(int expressionType, Class<?> resultType, @NonNull List<ParameterExpression> params) {
		super(expressionType, resultType);

		this.parameters = params;
	}

	public List<ParameterExpression> getParameters() {
		return parameters;
	}
}
