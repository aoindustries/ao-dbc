/*
 * ao-dbc - Simplified JDBC access for simplified code.
 * Copyright (C) 2010, 2011, 2015, 2020, 2021  AO Industries, Inc.
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
package com.aoapps.dbc;

import com.aoapps.lang.EmptyArrays;
import com.aoapps.lang.Throwables;
import com.aoapps.lang.i18n.Resources;
import java.io.Serializable;
import java.sql.SQLNonTransientException;

/**
 * Thrown when no row available and a row is required.
 */
public class NoRowException extends SQLNonTransientException {

	private static final long serialVersionUID = 5397878995581459678L;

	protected final Resources resources;
	protected final String key;
	protected final Serializable[] args;

	public NoRowException() {
		this("no data");
	}

	public NoRowException(String reason) {
		super(reason, "02000");
		resources = null;
		key = null;
		args = null;
	}

	public NoRowException(Resources resources, String key) {
		super(resources.getMessage(key), "02000");
		this.resources = resources;
		this.key = key;
		this.args = EmptyArrays.EMPTY_SERIALIZABLE_ARRAY;
	}

	public NoRowException(Resources resources, String key, Serializable... args) {
		super(resources.getMessage(key, (Object[])args), "02000");
		this.resources = resources;
		this.key = key;
		this.args = args;
	}

	public NoRowException(Throwable cause) {
		this("no data", cause);
	}

	public NoRowException(String reason, Throwable cause) {
		super(reason, "02000", cause);
		resources = null;
		key = null;
		args = null;
	}

	public NoRowException(Throwable cause, Resources resources, String key) {
		super(resources.getMessage(key), "02000", cause);
		this.resources = resources;
		this.key = key;
		this.args = EmptyArrays.EMPTY_SERIALIZABLE_ARRAY;
	}

	public NoRowException(Throwable cause, Resources resources, String key, Serializable... args) {
		super(resources.getMessage(key, (Object[])args), "02000", cause);
		this.resources = resources;
		this.key = key;
		this.args = args;
	}

	@Override
	public String getLocalizedMessage() {
		return (resources == null) ? super.getLocalizedMessage() : resources.getMessage(key, (Object[])args);
	}

	static {
		Throwables.registerSurrogateFactory(NoRowException.class, (template, cause) ->
			(template.resources == null)
				? new NoRowException(template.getMessage(), cause)
				: new NoRowException(cause, template.resources, template.key, template.args)
		);
	}
}
