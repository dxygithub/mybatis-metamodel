package com.github.thinwonton.mybatis.metamodel.tkmapper;

import com.github.thinwonton.mybatis.metamodel.core.register.EntityResolver;
import com.github.thinwonton.mybatis.metamodel.core.register.GlobalConfig;
import com.github.thinwonton.mybatis.metamodel.core.register.Table;
import com.github.thinwonton.mybatis.metamodel.core.register.TableField;
import com.github.thinwonton.mybatis.metamodel.core.util.RegisterUtils;
import com.github.thinwonton.mybatis.metamodel.core.util.StringUtils;
import com.github.thinwonton.mybatis.metamodel.core.util.Style;
import com.github.thinwonton.mybatis.metamodel.tkmapper.util.Utils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.type.JdbcType;
import tk.mybatis.mapper.annotation.ColumnType;
import tk.mybatis.mapper.annotation.NameStyle;
import tk.mybatis.mapper.annotation.RegisterMapper;
import tk.mybatis.mapper.util.SimpleTypeUtil;
import tk.mybatis.mapper.util.StringUtil;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TKMapperEntityResolver
 */
public class TKMapperEntityResolver implements EntityResolver {
    @Override
    public Class<?> getMappedEntityClass(MappedStatement mappedStatement) {
        String mappedStatementId = mappedStatement.getId();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //获取 mappedStatement 对应的 mapper类
        Class<?> mapperClass = RegisterUtils.getMapperClass(mappedStatementId, classLoader);
        Type[] types = mapperClass.getGenericInterfaces();

        //找到其中一个有 RegisterMapper 注解的接口，获取泛型
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                ParameterizedType t = (ParameterizedType) type;
                Type rawType = t.getRawType();
                Class<?> rawClass = (Class<?>) rawType;
                if (rawClass.isAnnotationPresent(RegisterMapper.class)) {
                    return (Class<?>) t.getActualTypeArguments()[0];
                }
            }
        }
        return null;
    }

    @Override
    public String resolveSimpleTableName(GlobalConfig globalConfig, Class<?> entityClass) {
        String tableName = null;
        if (entityClass.isAnnotationPresent(javax.persistence.Table.class)) {
            javax.persistence.Table tableAnnotation = entityClass.getAnnotation(javax.persistence.Table.class);
            if (StringUtils.isNotEmpty(tableAnnotation.name())) {
                tableName = tableAnnotation.name();
            }
        }

        if (tableName == null) {
            //style，NameStyle 注解优先于全局配置
            Style style = globalConfig.getStyle();
            if (entityClass.isAnnotationPresent(NameStyle.class)) {
                NameStyle nameStyle = entityClass.getAnnotation(NameStyle.class);
                style = Utils.transform(nameStyle.value());
            }

            tableName = entityClass.getSimpleName();
            //根据style转换table name
            tableName = StringUtils.transform(tableName, style);
        }

        return tableName;
    }

    @Override
    public Collection<TableField> resolveTableFields(GlobalConfig globalConfig, Table table, Class<?> entityClass) {
        //TODO 处理getter setter里面的属性
        List<TableField> tableFields = new ArrayList<>();
        Field[] declaredFields = entityClass.getDeclaredFields();
        for (Field field : declaredFields) {
            TableField tableField = resolveTableField(table, entityClass, field);
            if (tableField != null) {
                tableFields.add(tableField);
            }
        }
        return tableFields;
    }

    @Override
    public Table.CatalogSchemaInfo resolveTableCatalogSchemaInfo(GlobalConfig globalConfig, Class<?> entityClass) {
        Table.CatalogSchemaInfo catalogSchemaInfo = new Table.CatalogSchemaInfo();

        if (entityClass.isAnnotationPresent(javax.persistence.Table.class)) {
            javax.persistence.Table tableAnnotation = entityClass.getAnnotation(javax.persistence.Table.class);
            if (StringUtils.isNotEmpty(tableAnnotation.catalog()) || StringUtils.isNotEmpty(tableAnnotation.schema())) {
                catalogSchemaInfo.setCatalog(tableAnnotation.catalog());
                catalogSchemaInfo.setSchema(tableAnnotation.schema());
            }
        }

        catalogSchemaInfo.setGlobalCatalog(globalConfig.getCatalog());
        catalogSchemaInfo.setGlobalSchema(globalConfig.getSchema());
        return catalogSchemaInfo;
    }

    private TableField resolveTableField(Table table, Class<?> entityClass, Field field) {
        //排除 static，Transient
        //排除 @javax.persistence.Transient 注释
        //只接受基本包装类
        if (Modifier.isStatic(field.getModifiers())
                || Modifier.isTransient(field.getModifiers())
                || field.isAnnotationPresent(Transient.class)
                || !SimpleTypeUtil.isSimpleType(field.getType())) {
            return null;
        }

        TableField tableField = new TableField(table, field);

        //Id信息
        if (field.isAnnotationPresent(Id.class)) {
            tableField.markId();
        }

        //属性名
        tableField.setProperty(field.getName());

        //column name
        //如果从 @Column 不能获取，尝试从@ColumnType获取。都不能获取，从取名规则中获取
        String columnName = null;
        if (field.isAnnotationPresent(Column.class)) {
            Column column = field.getAnnotation(Column.class);
            columnName = column.name();
        }

        if (StringUtils.isEmpty(columnName) && field.isAnnotationPresent(ColumnType.class)) {
            ColumnType columnType = field.getAnnotation(ColumnType.class);
            if (StringUtils.isNotEmpty(columnType.column())) {
                columnName = columnType.column();
            }
        }

        if (StringUtils.isEmpty(columnName)) {
            //TODO 从命名规则中获取
            columnName = StringUtil.camelhumpToUnderline(field.getName());
        }
        tableField.setColumn(columnName);

        //java type
        tableField.setJavaType(field.getType());

        // jdbcType
        JdbcType jdbcType = JdbcType.UNDEFINED;
        if (field.isAnnotationPresent(ColumnType.class)) {
            ColumnType columnType = field.getAnnotation(ColumnType.class);
            jdbcType = columnType.jdbcType();
        }
        tableField.setJdbcType(jdbcType);

        return tableField;
    }

}
