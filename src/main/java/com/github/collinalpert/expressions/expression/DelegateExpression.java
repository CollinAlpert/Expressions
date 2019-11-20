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
 * Describes a lambda signature and an {@link Expression} delegate that returns {@link InvocableExpression}. The
 * delegate may encapsulate a parameter or {@link InvocationExpression}.
 */
@EqualsAndHashCode(callSuper = true)
public final class DelegateExpression extends InvocableExpression {

	private final Expression delegate;

	DelegateExpression(Class<?> resultType, @NonNull Expression delegate, List<ParameterExpression> params) {
		super(ExpressionType.Delegate, resultType, params);

		if (!InvocableExpression.class.isAssignableFrom(delegate.getResultType())) {
			throw new IllegalArgumentException("delegate");
		}

		this.delegate = delegate;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('{');
		b.append(getDelegate());
		b.append('}');
		return b.toString();
	}

	public Expression getDelegate() {
		return delegate;
	}
}
