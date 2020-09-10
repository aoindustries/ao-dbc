/*
 * ao-dbc - Simplified JDBC access for simplified code.
 * Copyright (C) 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-dbc.
 *
 * ao-dbc is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-dbc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-dbc.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.dbc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A set of object factories for various types.
 *
 * @author  AO Industries, Inc.
 */
final public class ObjectFactories {

	/**
	 * Make no instances.
	 */
	private ObjectFactories() {
	}

	public static final ObjectFactory<java.math.BigDecimal> BigDecimal = result -> result.getBigDecimal(1);

	public static final ObjectFactory<java.lang.Boolean> Boolean = result -> {
		boolean b = result.getBoolean(1);
		return result.wasNull() ? null : b;
	};

	public static final ObjectFactory<byte[]> ByteArray = result -> result.getBytes(1);

	public static final ObjectFactory<java.sql.Date> Date = result -> result.getDate(1);

	public static final ObjectFactory<java.lang.Double> Double = result -> {
		double d = result.getDouble(1);
		return result.wasNull() ? null : d;
	};

	public static final ObjectFactory<java.lang.Float> Float = result -> {
		float f = result.getFloat(1);
		return result.wasNull() ? null : f;
	};

	public static final ObjectFactory<java.lang.Integer> Integer = result -> {
		int i = result.getInt(1);
		return result.wasNull() ? null : i;
	};

	public static final ObjectFactory<java.lang.Long> Long = result -> {
		long l = result.getLong(1);
		return result.wasNull() ? null : l;
	};

	/**
	 *
	 * @deprecated  Please use a constructor lambda {@code Class::new}.
	 */
	@Deprecated
	public static class Object<T> implements ObjectFactory<T> {

		private final Class<? extends T> clazz;
		private final Constructor<? extends T> constructor;

		public Object(Class<? extends T> clazz) throws SQLException {
			this.clazz = clazz;
			try {
				this.constructor = clazz.getConstructor(ResultSet.class);
			} catch(NoSuchMethodException err) {
				throw new SQLException("Unable to find constructor: "+clazz.getName()+"(java.sql.ResultSet)", err);
			}
		}

		@Override
		@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
		public T createObject(ResultSet result) throws SQLException {
			try {
				return constructor.newInstance(result);
			} catch(InvocationTargetException e) {
				Throwable cause = e.getCause();
				if(cause instanceof Error) throw (Error)cause;
				if(cause instanceof RuntimeException) throw (RuntimeException)cause;
				if(cause instanceof SQLException) throw (SQLException)cause;
				throw new SQLException(clazz.getName() + "(java.sql.ResultSet)", cause == null ? e : cause);
			} catch(Error | RuntimeException e) {
				throw e;
			} catch(Throwable t) {
				throw new SQLException(clazz.getName() + "(java.sql.ResultSet)", t);
			}
		}

		/**
		 * @return  {@code false}
		 */
		@Override
		public boolean isNullable() {
			return false;
		}
	};

	public static final ObjectFactory<java.lang.Short> Short = result -> {
		short s = result.getShort(1);
		return result.wasNull() ? null : s;
	};

	public static final ObjectFactory<java.lang.String> String = result -> result.getString(1);

	public static final ObjectFactory<java.sql.Timestamp> Timestamp = result -> result.getTimestamp(1);

	/**
	 * Wraps an object factory, throwing {@link NullDataException} on any {@code null} result.
	 */
	private static class NotNullE<T,E extends Exception> implements ObjectFactoryE<T,E> {

		private final ObjectFactoryE<? extends T,E> objectFactory;

		private NotNullE(ObjectFactoryE<? extends T,E> objectFactory) {
			this.objectFactory = objectFactory;
		}

		@Override
		public T createObject(ResultSet result) throws NullDataException, SQLException, E {
			T obj = objectFactory.createObject(result);
			if(obj == null) throw new NullDataException(result);
			return obj;
		}

		/**
		 * @return  {@code false}
		 */
		@Override
		final public boolean isNullable() {
			return false;
		}
	}

	/**
	 * Wraps an object factory, throwing {@link NullDataException} on any {@code null} result.
	 */
	private static final class NotNull<T> extends NotNullE<T,RuntimeException> implements ObjectFactory<T> {
		
		private NotNull(ObjectFactory<? extends T> objectFactory) {
			super(objectFactory);
		}
	}

	/**
	 * Wraps an object factory, unless it is already not {@linkplain ObjectFactory#isNullable() nullable}.
	 *
	 * @return  If {@linkplain ObjectFactory#isNullable() nullable}, trusts the given object factory to not return
	 *          {@code null}, otherwise wraps.  Also wraps when assertions are enabled.
	 */
	@SuppressWarnings({"AssertWithSideEffects", "overloads"})
	public static <T,E extends Exception> ObjectFactoryE<T,E> notNull(ObjectFactoryE<T,E> objectFactory) {
		boolean wrap;
		if(objectFactory.isNullable()) {
			// Needs wrapping
			wrap = true;
		} else if(objectFactory instanceof NotNull) {
			// Already wrapped
			return objectFactory;
		} else {
			boolean assertionsEnabled = false;
			assert (assertionsEnabled = true) : "Detecting enabled assertions";
			// When assertions enabled, wrap for additional runtime verification
			wrap = assertionsEnabled;
		}
		return wrap ? new NotNullE<>(objectFactory) : objectFactory;
	}

	/**
	 * Wraps an object factory, unless it is already not {@linkplain ObjectFactory#isNullable() nullable}.
	 *
	 * @return  If {@linkplain ObjectFactory#isNullable() nullable}, trusts the given object factory to not return
	 *          {@code null}, otherwise wraps.  Also wraps when assertions are enabled.
	 */
	@SuppressWarnings({"AssertWithSideEffects", "overloads"})
	public static <T> ObjectFactory<T> notNull(ObjectFactory<T> objectFactory) {
		boolean wrap;
		if(objectFactory.isNullable()) {
			// Needs wrapping
			wrap = true;
		} else if(objectFactory instanceof NotNull) {
			// Already wrapped
			return objectFactory;
		} else {
			boolean assertionsEnabled = false;
			assert (assertionsEnabled = true) : "Detecting enabled assertions";
			// When assertions enabled, wrap for additional runtime verification
			wrap = assertionsEnabled;
		}
		return wrap ? new NotNull<>(objectFactory) : objectFactory;
	}
}
