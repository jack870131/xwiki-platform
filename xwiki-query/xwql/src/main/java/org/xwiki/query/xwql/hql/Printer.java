/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.query.xwql.hql;

import org.xwiki.query.xwql.QueryContext;
import org.xwiki.query.xwql.QueryContext.ObjectInfo;
import org.xwiki.query.xwql.QueryContext.PropertyInfo;

import org.xwiki.bridge.DocumentAccessBridge;

public class Printer
{
    QueryContext context;
    XWQLtoHQLTranslator parent;

    ObjectPrinter objectPrinter = new ObjectPrinter();
    PropertyPrinter propertyPrinter = new PropertyPrinter();

    StringBuilder from = new StringBuilder();
    StringBuilder where = new StringBuilder();

    public Printer(QueryContext context, XWQLtoHQLTranslator parent)
    {
        this.context = context;
        this.parent = parent;
    }

    QueryContext getContext()
    {
        return context;
    }

    DocumentAccessBridge getAccessBridge()
    {
        return parent.getDocumentAccessBridge();
    }

    public ObjectPrinter getObjectPrinter()
    {
        return objectPrinter;
    }

    public PropertyPrinter getPropertyPrinter()
    {
        return propertyPrinter;
    }

    String print() throws Exception {
        for (ObjectInfo obj : context.getObjects()) {
            getObjectPrinter().print(obj, this);
            for (PropertyInfo prop : obj.properties.values()) {
                getPropertyPrinter().print(prop, this);
            }
        }
        TreePrinter treePrinter = new TreePrinter(this);
        context.getTree().apply(treePrinter);
        return treePrinter.toString();
    }
}
