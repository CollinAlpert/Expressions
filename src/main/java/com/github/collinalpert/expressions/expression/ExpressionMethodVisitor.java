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

import org.objectweb.asm.Type;
import org.objectweb.asm.*;

import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

final class ExpressionMethodVisitor extends MethodVisitor {

	private static final Class<?>[] NumericTypeLookup = new Class<?>[]{Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE};
	private static final Class<?>[] NumericTypeLookup2 = new Class<?>[]{Byte.TYPE, Character.TYPE, Short.TYPE};
	private static final String LambdaMetafactoryClassInternalName = LambdaMetafactory.class.getName().replace('.', '/');

	private static final Map<Class<?>, Class<?>> _primitives;

	static {
		Map<Class<?>, Class<?>> primitives = new HashMap<>(8);
		primitives.put(Boolean.class, Boolean.TYPE);
		primitives.put(Byte.class, Byte.TYPE);
		primitives.put(Character.class, Character.TYPE);
		primitives.put(Double.class, Double.TYPE);
		primitives.put(Float.class, Float.TYPE);
		primitives.put(Integer.class, Integer.TYPE);
		primitives.put(Long.class, Long.TYPE);
		primitives.put(Short.class, Short.TYPE);

		_primitives = primitives;
	}

	private final HashMap<Label, List<ExpressionStack>> _branches = new HashMap<>();

	private final ExpressionClassVisitor _classVisitor;
	private final Class<?>[] _argTypes;
	private ExpressionStack _exprStack;
	private Supplier<ConstantExpression> _me;

	ExpressionMethodVisitor(ExpressionClassVisitor classVisitor, Supplier<ConstantExpression> me, Class<?>[] argTypes) {
		super(Opcodes.ASM9);
		_classVisitor = classVisitor;
		_me = me;
		_argTypes = argTypes;
	}

	private static Class<?> normalizePrimitive(Class<?> clz) {
		Class<?> primitive = _primitives.get(clz);

		return primitive != null ? primitive : clz;
	}

	private static void dup(ExpressionStack stack, int fromIndex, final int toIndex) {
		if (fromIndex == toIndex) {
			return;
		}

		Expression e = stack.get(stack.size() - fromIndex--);
		dup(stack, fromIndex, toIndex);
		stack.push(e);
	}

	private static ExpressionStack reduce(ExpressionStack first, ExpressionStack second) {
		int firstDepth = first.getDepth();
		int secondDepth = second.getDepth();

		if (firstDepth == secondDepth) {
			ExpressionStack.BranchExpression firstB = first.getParent();
			ExpressionStack.BranchExpression secondB = second.getParent();

			if (firstB == secondB) {
				ExpressionStack parentStack = firstB.getParent();
				parentStack.pop(); // branch

				Expression right = firstB.getTrue().pop();
				Expression left = firstB.getFalse().pop();
				assert normalizePrimitive(right.getResultType()) == normalizePrimitive(left.getResultType()) : "branches must evaluate to same type";
				parentStack.push(Expression.condition(firstB.getTest(), right, left));

				return parentStack;
			} else if (first.size() == 0 && second.size() == 0) {
				ExpressionStack.BranchExpression firstBB = firstB.getParent().getParent();
				ExpressionStack.BranchExpression secondBB = secondB.getParent().getParent();

				if (firstBB == secondBB) {
					ExpressionStack l;

					Expression fTest = firstB.getTest();
					if (firstB.getTrue() != first) {
						fTest = Expression.logicalNot(fTest);
						l = firstB.getTrue();
					} else {
						l = firstB.getFalse();
					}

					Expression sTest = secondB.getTest();
					if (secondB.getTrue() != second) {
						sTest = Expression.logicalNot(sTest);
						secondB.getTrue().reduce();
					} else {
						secondB.getFalse().reduce();
					}

					Expression rootTest = firstBB.getTest();
					if (firstBB.getTrue() != firstB.getParent()) {
						rootTest = Expression.logicalNot(rootTest);
					}

					rootTest = Expression.condition(rootTest, fTest, sTest);

					ExpressionStack parentStack = firstBB.getParent();

					ExpressionStack.BranchExpression be = new ExpressionStack.BranchExpression(parentStack, rootTest, first, l);

					parentStack.pop(); // old branch

					parentStack.add(be);

					return first;
				}
			}
		} else if (first.size() == 0 && second.size() == 0) {
			ExpressionStack older;
			ExpressionStack younger;

			if (firstDepth > secondDepth) {
				older = second;
				younger = first;
			} else {
				older = first;
				younger = second;
			}

			final boolean trueB = older.getParent().getTrue() == older;

			ExpressionStack.BranchExpression youngerBranch = younger.getParent();
			Expression youngTest = youngerBranch.getTest();

			ExpressionStack other;
			if (younger.getParent().get(trueB) != younger) {
				youngTest = Expression.logicalNot(youngTest);
				other = youngerBranch.get(trueB);
			} else {
				other = youngerBranch.get(!trueB);
			}

			Expression test = Expression.logicalAnd(older.getParent().getTest(), youngTest);

			if (!trueB) {
				test = Expression.logicalNot(test);
			}

			ExpressionStack parentStack = older.getParent().getParent();

			ExpressionStack.BranchExpression be = new ExpressionStack.BranchExpression(parentStack, test, older, other);

			parentStack.pop(); // old branch

			parentStack.add(be);

			return older;
		}

		return null;
	}

	private static ExpressionStack reduce(List<ExpressionStack> bl) {
		int index = bl.size() - 1;
		ExpressionStack second = bl.remove(index--);
		if (index < 0) {
			return second;
		}

		ExpressionStack first = bl.get(index);
		ExpressionStack reduced = reduce(first, second);
		if (reduced != null) {
			bl.set(index, reduced);
			return reduce(bl);
		}

		first = reduce(bl);

		return reduce(first, second);
	}

	static RuntimeException notLambda(int opcode) {
		String opcodeName = Integer.toString(opcode);
		Field[] ops = Opcodes.class.getFields();
		for (Field f : ops) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == Integer.TYPE) {
				try {
					int test = f.getInt(null);
					if (test == opcode) {
						opcodeName = f.getName();
						break;
					}
				} catch (IllegalAccessException e) {
					// suppress;
					break;
				}
			}
		}

		return new IllegalArgumentException("Not a lambda expression. Opcode " + opcodeName + " is illegal.");
	}

	private List<ExpressionStack> getBranchUsers(Label label) {
		return _branches.computeIfAbsent(label, k -> new ArrayList<>());
	}

	private void go(Label label) {
		getBranchUsers(label).add(_exprStack);

		_exprStack = null;
	}

	private void branch(Label label, Expression test) {
		List<ExpressionStack> bl = getBranchUsers(label);

		ExpressionStack.BranchExpression br = new ExpressionStack.BranchExpression(_exprStack, test, label);
		_exprStack.push(br);

		ExpressionStack left = br.getFalse();
		bl.add(left);
		_exprStack = br.getTrue();
	}

	private void pushZeroConstantOrReduce() {
		Expression e = _exprStack.peek();
		if (e.getExpressionType() == ExpressionType.Subtract) {// reduce
			BinaryExpression be = (BinaryExpression) _exprStack.pop();
			_exprStack.push(be.getFirst());
			_exprStack.push(be.getSecond());

			return;
		}
		Class<?> type = _exprStack.peek().getResultType();
		Object value;

		if (type == Byte.TYPE) {
			value = (byte) 0;
		} else if (type == Double.TYPE) {
			value = 0d;
		} else if (type == Float.TYPE) {
			value = 0f;
		} else if (type == Integer.TYPE) {
			value = 0;
		} else if (type == Long.TYPE) {
			value = 0L;
		} else if (type == Short.TYPE) {
			value = (short) 0;
		} else if (type == Boolean.TYPE || type == Boolean.class) {
			value = Boolean.FALSE;
		} else {
			throw new IllegalStateException(type.toString());
		}

		_exprStack.push(Expression.constant(value, type));
	}

	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		return null;
	}

	@Override
	public AnnotationVisitor visitAnnotationDefault() {
		return null;
	}

	@Override
	public void visitAttribute(Attribute attr) {

	}

	@Override
	public void visitCode() {
		_exprStack = new ExpressionStack();
	}

	@Override
	public void visitEnd() {
		visitLabel(null);
		assert _exprStack.size() == 1;

		_classVisitor.setResult(_exprStack.pop());
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		Expression e;
		boolean isSyntheticConstant = false;
		switch (opcode) {
			case Opcodes.GETFIELD:
				Expression instance = _exprStack.pop();
				try {
					e = Expression.get(instance, name);
				} catch (NoSuchFieldException nsfe) {
					throw new RuntimeException(nsfe);
				}
				if (instance.getExpressionType() == ExpressionType.Constant && instance.getResultType().isSynthetic()) {
					isSyntheticConstant = true;
				}
				break;
			case Opcodes.GETSTATIC:
				try {
					Class<?> containingClass = _classVisitor.getClass(Type.getObjectType(owner));
					e = Expression.get(containingClass, name);
					if (containingClass.isSynthetic()) {
						isSyntheticConstant = true;
					}
				} catch (NoSuchFieldException nsfe) {
					throw new RuntimeException(nsfe);
				}
				break;
			case Opcodes.PUTFIELD:
			case Opcodes.PUTSTATIC:
			default:
				throw notLambda(opcode);
		}

		if (isSyntheticConstant) {
			// evaluate now, since has no meaning to the user in the field form
			Object value = e.accept(Interpreter.Instance).apply(null);
			e = Expression.constant(value, e.getResultType());
		}

		_exprStack.push(e);
	}

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
		throw notLambda(type);
	}

	@Override
	public void visitIincInsn(int arg0, int arg1) {
		throw notLambda(Opcodes.IINC);
	}

	@Override
	public void visitInsn(int opcode) {
		Expression e;
		Expression first;
		Expression second;
		switch (opcode) {
			case Opcodes.ARRAYLENGTH:
				e = Expression.arrayLength(_exprStack.pop());
				break;
			case Opcodes.ACONST_NULL:
				e = Expression.constant(null, Object.class);
				break;
			case Opcodes.IALOAD:
			case Opcodes.LALOAD:
			case Opcodes.FALOAD:
			case Opcodes.DALOAD:
			case Opcodes.AALOAD:
			case Opcodes.BALOAD:
			case Opcodes.CALOAD:
			case Opcodes.SALOAD:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.arrayIndex(second, first);
				break;
			case Opcodes.DCONST_0:
				e = Expression.constant(0d, Double.TYPE);
				break;
			case Opcodes.DCONST_1:
				e = Expression.constant(1d, Double.TYPE);
				break;
			case Opcodes.FCMPG:
			case Opcodes.FCMPL:
			case Opcodes.DCMPG:
			case Opcodes.DCMPL:
			case Opcodes.LCMP:
			case Opcodes.ISUB:
			case Opcodes.LSUB:
			case Opcodes.FSUB:
			case Opcodes.DSUB:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.subtract(second, first);
				break;
			case Opcodes.FCONST_0:
				e = Expression.constant(0f, Float.TYPE);
				break;
			case Opcodes.FCONST_1:
				e = Expression.constant(1f, Float.TYPE);
				break;
			case Opcodes.FCONST_2:
				e = Expression.constant(2f, Float.TYPE);
				break;
			case Opcodes.ICONST_M1:
				e = Expression.constant(-1, Integer.TYPE);
				break;
			case Opcodes.ICONST_0:
				e = Expression.constant(0, Integer.TYPE);
				break;
			case Opcodes.ICONST_1:
				e = Expression.constant(1, Integer.TYPE);
				break;
			case Opcodes.ICONST_2:
				e = Expression.constant(2, Integer.TYPE);
				break;
			case Opcodes.ICONST_3:
				e = Expression.constant(3, Integer.TYPE);
				break;
			case Opcodes.ICONST_4:
				e = Expression.constant(4, Integer.TYPE);
				break;
			case Opcodes.ICONST_5:
				e = Expression.constant(5, Integer.TYPE);
				break;
			case Opcodes.LCONST_0:
				e = Expression.constant(0L, Long.TYPE);
				break;
			case Opcodes.LCONST_1:
				e = Expression.constant(1L, Long.TYPE);
				break;
			case Opcodes.IADD:
			case Opcodes.LADD:
			case Opcodes.FADD:
			case Opcodes.DADD:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.add(second, first);
				break;
			case Opcodes.IMUL:
			case Opcodes.LMUL:
			case Opcodes.FMUL:
			case Opcodes.DMUL:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.multiply(second, first);
				break;
			case Opcodes.IDIV:
			case Opcodes.LDIV:
			case Opcodes.FDIV:
			case Opcodes.DDIV:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.divide(second, first);
				break;
			case Opcodes.IREM:
			case Opcodes.LREM:
			case Opcodes.FREM:
			case Opcodes.DREM:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.modulo(second, first);
				break;
			case Opcodes.INEG:
			case Opcodes.LNEG:
			case Opcodes.FNEG:
			case Opcodes.DNEG:
				first = _exprStack.pop();
				e = Expression.negate(first);
				break;
			case Opcodes.ISHL:
			case Opcodes.LSHL:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.leftShift(second, first);
				break;
			case Opcodes.ISHR:
			case Opcodes.LSHR:
			case Opcodes.IUSHR:
			case Opcodes.LUSHR:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.rightShift(second, first);
				break;
			case Opcodes.IAND:
			case Opcodes.LAND:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.bitwiseAnd(second, first);
				break;
			case Opcodes.IOR:
			case Opcodes.LOR:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.bitwiseOr(second, first);
				break;
			case Opcodes.IXOR:
			case Opcodes.LXOR:
				first = _exprStack.pop();
				second = _exprStack.pop();
				e = Expression.exclusiveOr(second, first);
				break;
			case Opcodes.I2B:
			case Opcodes.I2C:
			case Opcodes.I2S:
				first = _exprStack.pop();
				e = Expression.convert(first, NumericTypeLookup2[opcode - Opcodes.I2B]);
				break;
			case Opcodes.I2L:
			case Opcodes.I2F:
			case Opcodes.I2D:
				first = _exprStack.pop();
				e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.I2L + 1]);
				break;
			case Opcodes.L2I:
			case Opcodes.L2F:
			case Opcodes.L2D:
				int l2l = opcode > Opcodes.L2I ? 1 : 0;
				first = _exprStack.pop();
				e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.L2I + l2l]);
				break;
			case Opcodes.F2I:
			case Opcodes.F2L:
			case Opcodes.F2D:
				int f2f = opcode == Opcodes.F2D ? 1 : 0;
				first = _exprStack.pop();
				e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.F2I + f2f]);
				break;
			case Opcodes.D2I:
			case Opcodes.D2L:
			case Opcodes.D2F:
				first = _exprStack.pop();
				e = Expression.convert(first, NumericTypeLookup[opcode - Opcodes.D2I]);
				break;
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
			case Opcodes.ARETURN:
				go(null);

				return;
			case Opcodes.SWAP:
				first = _exprStack.pop();
				second = _exprStack.pop();
				_exprStack.push(first);
				_exprStack.push(second);
			case Opcodes.DUP:
			case Opcodes.DUP_X1:
			case Opcodes.DUP_X2:
			case Opcodes.DUP2:
			case Opcodes.DUP2_X1:
			case Opcodes.DUP2_X2:
				// our stack is not divided to words
				int base = (opcode - Opcodes.DUP) % 3;
				base++;
				dup(_exprStack, base, base - 1);
				return;
			case Opcodes.NOP:
				return;
			case Opcodes.RETURN:
			default:
				throw notLambda(opcode);
		}

		_exprStack.push(e);
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		switch (opcode) {
			case Opcodes.BIPUSH:
			case Opcodes.SIPUSH:
				_exprStack.push(Expression.constant(operand, Integer.TYPE));
				break;
			default:
				throw notLambda(opcode);
		}
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		int etype;
		switch (opcode) {
			case Opcodes.GOTO:
				go(label);

				return;
			default:
			case Opcodes.JSR:
				throw notLambda(opcode);
			case Opcodes.IFEQ:
				etype = ExpressionType.NotEqual; // Equal
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IFNE:
				etype = ExpressionType.Equal; // NotEqual
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IFLT:
				etype = ExpressionType.GreaterThanOrEqual; // LessThan
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IFGE:
				etype = ExpressionType.LessThan; // GreaterThanOrEqual
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IFGT:
				etype = ExpressionType.LessThanOrEqual; // GreaterThan
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IFLE:
				etype = ExpressionType.GreaterThan; // LessThanOrEqual
				pushZeroConstantOrReduce();
				break;
			case Opcodes.IF_ICMPEQ:
			case Opcodes.IF_ACMPEQ: // ??
				etype = ExpressionType.NotEqual; // Equal
				break;
			case Opcodes.IF_ICMPNE:
			case Opcodes.IF_ACMPNE: // ??
				etype = ExpressionType.Equal; // NotEqual
				break;
			case Opcodes.IF_ICMPLT:
				etype = ExpressionType.GreaterThanOrEqual; // LessThan
				break;
			case Opcodes.IF_ICMPGE:
				etype = ExpressionType.LessThan; // GreaterThanOrEqual
				break;
			case Opcodes.IF_ICMPGT:
				etype = ExpressionType.LessThanOrEqual; // GreaterThan
				break;
			case Opcodes.IF_ICMPLE:
				etype = ExpressionType.GreaterThan; // LessThanOrEqual
				break;
			case Opcodes.IFNULL:
			case Opcodes.IFNONNULL:
				Expression e = Expression.isNull(_exprStack.pop());
				if (opcode == Opcodes.IFNULL) { // IFNONNULL
					e = Expression.logicalNot(e);
				}

				branch(label, e);

				return;
		}

		Expression second = _exprStack.pop();
		Expression first = _exprStack.pop();
		Expression e = Expression.binary(etype, first, second);

		branch(label, e);
	}

	@Override
	public void visitLabel(Label label) {
		List<ExpressionStack> bl = _branches.get(label);
		_branches.remove(label);

		for (int i = bl.size() - 1; i >= 0; i--) {
			ExpressionStack es = bl.get(i);
			if (es.isReduced()) {
				bl.remove(i);
			}
		}

		if (_exprStack != null) {
			bl.add(_exprStack);
		}

		_exprStack = reduce(bl);
		assert _exprStack != null;
	}

	@Override
	public void visitLdcInsn(Object cst) {
		Class<?> type = _primitives.get(cst.getClass());
		if (type == null) {
			type = cst.getClass();
		}

		_exprStack.push(Expression.constant(cst, type));
	}

	@Override
	public void visitLineNumber(int line, Label start) {

	}

	@Override
	public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
		throw notLambda(-1);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		throw notLambda(Opcodes.LOOKUPSWITCH);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		// assert maxLocals == 0;
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
		String bootMethod = bootstrapMethodHandle.getName();
		if (!bootstrapMethodHandle.getOwner().equals(LambdaMetafactoryClassInternalName)
				|| !"Metafactory".regionMatches(true, 0, bootMethod, bootMethod.length() - "Metafactory".length(), "Metafactory".length())) {
			throw new UnsupportedOperationException("Unsupported bootstrapMethodHandle: " + bootstrapMethodHandle);
		}

		// the following code creates partial applied lambda of bootstrapMethodArguments[2] Type

		Handle handle = (Handle) bootstrapMethodArguments[1];
		Type objectType = Type.getObjectType(handle.getOwner());

		Type[] argsTypes = Type.getArgumentTypes(descriptor);
		Expression[] arguments = createArguments(argsTypes);

		boolean[] hasThis = new boolean[1];
		boolean mayHaveThis = arguments.length > 0 && arguments[0] instanceof ConstantExpression;
		Expression optionalThis = mayHaveThis ? arguments[0] : null;
		var lambda = ExpressionClassCracker.getInstance().lambdaFromClassLoader(_classVisitor.getLoader(), objectType.getInternalName(),
				mayHaveThis ? () -> {
					hasThis[0] = true;
					return (ConstantExpression) optionalThis;
				} : null, handle.getName(), handle.getDesc());

		if (hasThis[0]) {
			arguments = Arrays.copyOfRange(arguments, 1, arguments.length);
			argsTypes = Arrays.copyOfRange(argsTypes, 1, argsTypes.length);
		}

		if (argsTypes.length == 0) {
			_exprStack.push(lambda);
			return;
		}

		Class<?>[] parameterTypes = getParameterTypes(argsTypes);
		convertArguments(arguments, parameterTypes);

		List<ParameterExpression> params = new ArrayList<>(parameterTypes.length);
		for (int i = 0; i < parameterTypes.length; i++) {
			params.add(Expression.parameter(parameterTypes[i], i));
		}

		var partial = Expression.lambda(lambda.getResultType(), lambda, params);

		InvocationExpression e = Expression.invoke(partial, arguments);

		_exprStack.push(e);
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
		Type[] argsTypes = Type.getArgumentTypes(desc);
		Expression[] arguments = createArguments(argsTypes);

		Expression e;

		switch (opcode) {
			case Opcodes.INVOKESPECIAL:
				if (name.equals("<init>")) {
					Class<?>[] parameterTypes = getParameterTypes(argsTypes);
					convertArguments(arguments, parameterTypes);
					try {
						e = Expression.newInstance(_exprStack.pop().getResultType(), parameterTypes, arguments);
					} catch (NoSuchMethodException nsme) {
						throw new RuntimeException(nsme);
					}
					_exprStack.pop(); // going to re-add it, which is not the JVM
					// semantics
					break;
				}
			case Opcodes.INVOKEVIRTUAL:
			case Opcodes.INVOKEINTERFACE:
				try {
					Class<?> lambdaClass = _classVisitor.getClass(Type.getObjectType(owner));
					Expression instance = _exprStack.pop();
					if (instance.getExpressionType() == ExpressionType.Constant) {
						Object value = ((ConstantExpression) instance).getValue();
						if (value instanceof SerializedLambda) {
							SerializedLambda serialized = (SerializedLambda) value;
							ClassLoader lambdaClassLoader = _classVisitor.getLoader();
							Class<?> serializedClass;
							try {
								serializedClass = lambdaClassLoader.loadClass(serialized.functionalInterfaceClass.replace('/', '.'));
							} catch (ClassNotFoundException cnfe) {
								throw new RuntimeException(cnfe);
							}

							if (!lambdaClass.isAssignableFrom(serializedClass)) {
								throw new ClassCastException(serializedClass + " cannot be cast to " + lambdaClass);
							}

							if (!serialized.functionalInterfaceMethodName.equals(name)) {
								throw new NoSuchMethodException(name);
							}

							var lambda = ExpressionClassCracker.getInstance().lambda(serialized, lambdaClassLoader);
							Class<?>[] parameterTypes = lambda.getParameters().stream().map(ParameterExpression::getResultType).toArray(Class[]::new);
							convertArguments(arguments, parameterTypes);
							e = Expression.invoke(lambda, arguments);

							break;
						} else {
							Class<?> instanceClass = value.getClass();
							if (instanceClass.isSynthetic()) {
								e = Expression.invoke(ExpressionClassCracker.getInstance().lambdaFromFileSystem(value,
										instance.getResultType().getDeclaredMethod(name, getParameterTypes(argsTypes))), arguments);

								break;
							}
						}
					}

					Class<?>[] parameterTypes = getParameterTypes(argsTypes);
					convertArguments(arguments, parameterTypes);
					e = Expression.invoke(TypeConverter.convert(instance, lambdaClass), name, parameterTypes, arguments);

				} catch (NoSuchMethodException nsme) {
					throw new RuntimeException(nsme);
				}

				break;
			case Opcodes.INVOKESTATIC:
				Class<?>[] parameterTypes = getParameterTypes(argsTypes);
				convertArguments(arguments, parameterTypes);
				try {
					Class<?> targetType = _classVisitor.getClass(Type.getObjectType(owner));
					e = targetType.isSynthetic() ? Expression.invoke(
							ExpressionClassCracker.getInstance().lambdaFromFileSystem(null, targetType.getDeclaredMethod(name, getParameterTypes(argsTypes))), arguments)
							: Expression.invoke(targetType, name, parameterTypes, arguments);
				} catch (NoSuchMethodException nsme) {
					throw new RuntimeException(nsme);
				}

				break;
			default:
				throw new IllegalArgumentException("opcode: " + opcode);
		}

		_exprStack.push(e);
	}

	private void convertArguments(Expression[] arguments, Class<?>[] parameterTypes) {
		for (int i = 0; i < arguments.length; i++) {
			arguments[i] = TypeConverter.convert(arguments[i], parameterTypes[i]);
		}
	}

	private Expression[] createArguments(Type[] argsTypes) {
		Expression[] arguments = new Expression[argsTypes.length];
		for (int i = argsTypes.length; i > 0; ) {
			i--;
			arguments[i] = _exprStack.pop();
		}

		return arguments;
	}

	private Class<?>[] getParameterTypes(Type[] argsTypes) {
		Class<?>[] parameterTypes = new Class<?>[argsTypes.length];
		for (int i = 0; i < argsTypes.length; i++) {
			parameterTypes[i] = _classVisitor.getClass(argsTypes[i]);
		}

		return parameterTypes;
	}

	// @Overrides
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		throw notLambda(Opcodes.MULTIANEWARRAY);
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int arg0, String arg1, boolean arg2) {
		return null;
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		throw notLambda(Opcodes.TABLESWITCH);
	}

	@Override
	public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
		throw notLambda(-2);
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		Class<?> resultType = _classVisitor.getClass(Type.getObjectType(type));
		Expression e;
		switch (opcode) {
			case Opcodes.NEW:
				e = Expression.constant(null, resultType);

				break;
			case Opcodes.CHECKCAST:
				if (resultType == Object.class) {
					// there is no point in casting to object
					return;
				}
				// TODO
				return;
			case Opcodes.ANEWARRAY:
			default:
				throw notLambda(opcode);
			case Opcodes.INSTANCEOF:
				e = Expression.instanceOf(_exprStack.pop(), resultType);

				break;
		}

		_exprStack.push(e);
	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		if (_me != null) {
			if (var == 0) {
				_exprStack.push(_me.get());
				return;
			}

			var--;
		}

		// 64 bit values hold 2 slots on the stack - compensate it
		for (int i = 0; i < var; i++) {
			Class<?> clazz = _argTypes[i];
			if (clazz == Long.TYPE || clazz == Double.TYPE) {
				var--;
			}
		}

		Class<?> type;
		switch (opcode) {
			case Opcodes.ISTORE:
			case Opcodes.LSTORE:
			case Opcodes.FSTORE:
			case Opcodes.DSTORE:
			case Opcodes.ASTORE:
			case Opcodes.RET:
			default:
				throw notLambda(opcode);
			case Opcodes.ILOAD:
				type = Integer.TYPE;
				break;
			case Opcodes.LLOAD:
				type = Long.TYPE;
				break;
			case Opcodes.FLOAD:
				type = Float.TYPE;
				break;
			case Opcodes.DLOAD:
				type = Double.TYPE;
				break;
			case Opcodes.ALOAD:
				type = _argTypes[var];
				break;
		}

		_exprStack.push(Expression.parameter(type, var));
	}

}
