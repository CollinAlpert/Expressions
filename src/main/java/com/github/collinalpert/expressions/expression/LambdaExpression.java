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
import java.util.function.Function;

/**
 * Describes a lambda expression. This captures a block of code that is similar to a method body.
 * <p>
 * Use {@link #parse(Object)} method to get a lambda expression tree.
 * </p>
 */

@EqualsAndHashCode(callSuper = true)
public final class LambdaExpression extends InvocableExpression {

	private final Expression body;

	LambdaExpression(Class<?> resultType, @NonNull Expression body, List<ParameterExpression> params) {
		super(ExpressionType.Lambda, resultType, params);

		if (!TypeConverter.isAssignable(resultType, body.getResultType())) {
			throw new IllegalArgumentException(body.getResultType() + " is not assignable to " + resultType);
		}

		this.body = body;
	}

	/**
	 * Creates {@link LambdaExpression} representing the lambda expression tree.
	 *
	 * @param lambda - the lambda
	 * @return {@link LambdaExpression} representing the lambda expression tree.
	 */
	public static LambdaExpression parse(Object lambda) {
		return ExpressionClassCracker.getInstance().lambda(lambda);
	}

	/**
	 * Produces a {@link Function} that represents the lambda expression.
	 *
	 * @return {@link Function} that represents the lambda expression.
	 */
	public Function<Object[], ?> compile() {
		final Function<Object[], ?> f = accept(Interpreter.Instance);
		return (Object[] pp) -> {
			Function<Object[], ?> f1 = (Function<Object[], ?>) f.apply(pp);

			return f1.apply(null);
		};
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		List<ParameterExpression> arguments = getParameters();
		if (arguments.size() > 0) {
			b.append('(');
			for (int i = 0; i < arguments.size(); i++) {
				if (i > 0) {
					b.append(',');
					b.append(' ');
				}

				ParameterExpression pe = arguments.get(i);
				b.append(pe.getResultType().getName());
				b.append(' ');
				b.append(pe.toString());
			}
			b.append(')');
		}
		b.append(" -> ");
		b.append(getBody().toString());
		b.append('}');

		return b.toString();
	}

	public Expression getBody() {
		return body;
	}

}
