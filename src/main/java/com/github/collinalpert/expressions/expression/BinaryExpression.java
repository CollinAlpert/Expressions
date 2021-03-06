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

/**
 * Represents an expression that has a binary operator.
 */
@EqualsAndHashCode(callSuper = true)
public final class BinaryExpression extends UnaryExpression {

	private final Expression operator;
	private final Expression second;

	BinaryExpression(int expressionType, Class<?> resultType, Expression operator, Expression first, @NonNull Expression second) {
		super(expressionType, resultType, first);

		if (expressionType == ExpressionType.Conditional) {
			if (operator == null) {
				throw new IllegalArgumentException(new NullPointerException("operator"));
			}
		}

		this.operator = operator;
		this.second = second;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('(');
		if (getOperator() != null) {
			b.append(getOperator().toString());
			b.append(' ');
			b.append(ExpressionType.toString(getExpressionType()));
			b.append(' ');

			b.append(getFirst().toString());
			b.append(' ');

			b.append(':');
		} else {
			b.append(getFirst().toString());
			b.append(' ');
			b.append(ExpressionType.toString(getExpressionType()));
		}
		b.append(' ');
		b.append(getSecond().toString());
		b.append(')');
		return b.toString();
	}

	public Expression getOperator() {
		return operator;
	}

	public Expression getSecond() {
		return second;
	}
}
