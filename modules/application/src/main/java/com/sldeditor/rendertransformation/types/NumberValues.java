/*
 * SLD Editor - The Open Source Java SLD Editor
 *
 * Copyright (C) 2017, SCISYS UK Limited
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sldeditor.rendertransformation.types;

import java.util.Arrays;
import java.util.List;

import org.geotools.filter.AttributeExpressionImpl;
import org.geotools.filter.FunctionExpressionImpl;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.MathExpressionImpl;
import org.opengis.filter.expression.Expression;

import com.sldeditor.filter.v2.expression.TypeManager;
import com.sldeditor.ui.detail.config.FieldConfigBase;
import com.sldeditor.ui.detail.config.FieldConfigCommonData;
import com.sldeditor.ui.detail.config.FieldConfigDouble;
import com.sldeditor.ui.detail.config.FieldConfigInteger;
import com.sldeditor.ui.detail.config.symboltype.SymbolTypeConfig;

/**
 * The Class NumberValues.
 *
 * @author Robert Ward (SCISYS)
 */
public class NumberValues extends BaseValue implements RenderTransformValueInterface {

    /** The value. */
    private Number value = null;

    /**
     * Instantiates a new number values.
     */
    public NumberValues() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#setDefaultValue(java.lang.Object)
     */
    @Override
    public void setDefaultValue(Object defaultValue) {
        this.value = (Number) defaultValue;
    }

    /**
     * Populate symbol type.
     *
     * @param symbolTypeConfig the symbol type config
     */
    protected void populateSymbolType(SymbolTypeConfig symbolTypeConfig) {
        // Do nothing
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#getExpression()
     */
    @Override
    public Expression getExpression() {
        if(expression != null)
        {
            return expression;
        }
        return filterFactory.literal(value);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#setValue(java.lang.Object)
     */
    @Override
    public void setValue(Object aValue) {
        this.value = null;
        this.expression = null;

        if (aValue instanceof Number) {
            this.value = (Number) aValue;
        }
        else if(aValue instanceof LiteralExpressionImpl)
        {
            LiteralExpressionImpl literal = (LiteralExpressionImpl)aValue;
            value = literal.evaluate(value, Number.class);
        }
        else if((aValue instanceof AttributeExpressionImpl) ||
                (aValue instanceof FunctionExpressionImpl) ||
                (aValue instanceof MathExpressionImpl))
        {
            this.expression = (Expression) aValue;
        }
    }

    /* (non-Javadoc)
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#getType()
     */
    @Override
    public List<Class<?>> getType() {
        return Arrays.asList(Number.class);
    }

    /* (non-Javadoc)
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#getField(com.sldeditor.ui.detail.config.FieldConfigCommonData)
     */
    @Override
    public FieldConfigBase getField(FieldConfigCommonData commonData) {
        Class<?> filterType = TypeManager.getInstance().getDataType();
        if((filterType == Float.class) || (filterType == Double.class))
        {
            return new FieldConfigDouble(commonData);
        }
        else
        {
            return new FieldConfigInteger(commonData);
        }
    }

    /* (non-Javadoc)
     * @see com.sldeditor.rendertransformation.types.RenderTransformValueInterface#createInstance()
     */
    @Override
    public RenderTransformValueInterface createInstance() {
        return new NumberValues();
    }
}
