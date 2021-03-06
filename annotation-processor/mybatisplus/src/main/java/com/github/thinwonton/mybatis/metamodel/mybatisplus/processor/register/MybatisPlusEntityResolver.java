package com.github.thinwonton.mybatis.metamodel.mybatisplus.processor.register;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.github.thinwonton.mybatis.metamodel.core.MybatisPlusConfig;
import com.github.thinwonton.mybatis.metamodel.core.register.EntityResolver;
import com.github.thinwonton.mybatis.metamodel.core.register.GlobalConfig;
import com.github.thinwonton.mybatis.metamodel.core.register.Table;
import com.github.thinwonton.mybatis.metamodel.core.register.TableField;
import com.github.thinwonton.mybatis.metamodel.core.util.RegisterUtils;
import com.github.thinwonton.mybatis.metamodel.core.util.StringUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MybatisPlusEntityResolver
 */
public class MybatisPlusEntityResolver implements EntityResolver {
    @Override
    public Class<?> getMappedEntityClass(MappedStatement mappedStatement) {
        String mappedStatementId = mappedStatement.getId();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //获取 mappedStatement 对应的 mapper类
        Class<?> mapperClass = RegisterUtils.getMapperClass(mappedStatementId, classLoader);

        //通过 BaseMapper 或者 Mapper 接口，获取entity
        Type[] types = mapperClass.getGenericInterfaces();
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                ParameterizedType t = (ParameterizedType) type;
                Type rawType = t.getRawType();
                Class<?> rawClass = (Class<?>) rawType;
                if (BaseMapper.class.isAssignableFrom(rawClass) || Mapper.class.isAssignableFrom(rawClass)) {
                    //获取泛型
                    return (Class<?>) t.getActualTypeArguments()[0];
                }
            }
        }

        return null;
    }

    @Override
    public String resolveSimpleTableName(GlobalConfig globalConfig, Class<?> entityClass) {
        MybatisPlusConfig mybatisPlusConfig = globalConfig.getMybatisPlusConfig();

        String tableName = null;
        if (entityClass.isAnnotationPresent(TableName.class)) {
            TableName tableNameAnnotation = entityClass.getAnnotation(TableName.class);
            if (StringUtils.isNotEmpty(tableNameAnnotation.value())) {
                tableName = tableNameAnnotation.value();
            }
        }

        if (StringUtils.isEmpty(tableName)) {
            tableName = entityClass.getSimpleName();
            // 根据全局配置转换获取表名
            tableName = StringUtils.transform(tableName, mybatisPlusConfig.getStyle());
        }
        return tableName;
    }

    @Override
    public Collection<TableField> resolveTableFields(GlobalConfig globalConfig, Table table, Class<?> entityClass) {
        List<TableField> tableFields = new ArrayList<>();
        Field[] declaredFields = entityClass.getDeclaredFields();
        for (Field field : declaredFields) {
            if (!filter(field)) {
                TableField tableField = resolveTableField(globalConfig, table, entityClass, field);
                tableFields.add(tableField);
            }
        }
        return tableFields;
    }

    @Override
    public Table.CatalogSchemaInfo resolveTableCatalogSchemaInfo(GlobalConfig globalConfig, Class<?> entityClass) {
        Table.CatalogSchemaInfo catalogSchemaInfo = new Table.CatalogSchemaInfo();

        if (entityClass.isAnnotationPresent(TableName.class)) {
            TableName table = entityClass.getAnnotation(TableName.class);
            catalogSchemaInfo.setSchema(table.schema());
        }

        MybatisPlusConfig mybatisPlusConfig = globalConfig.getMybatisPlusConfig();

        catalogSchemaInfo.setGlobalSchema(mybatisPlusConfig.getSchema());

        return catalogSchemaInfo;
    }

    private boolean filter(Field field) {
        //排除 static，Transient
        //TODO 过滤声明类型
        com.baomidou.mybatisplus.annotation.TableField annotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        boolean markedNotTableField = (annotation != null && !annotation.exist());
        if (Modifier.isStatic(field.getModifiers())
                || Modifier.isTransient(field.getModifiers())
                || markedNotTableField) {
            return true;
        }
        return false;
    }

    private TableField resolveTableField(GlobalConfig globalConfig, Table table, Class<?> entityClass, Field field) {

        TableField tableField = new TableField();
        tableField.setTable(table);
        tableField.setField(field);

        //Id信息
        if (field.isAnnotationPresent(TableId.class)) {
            tableField.markId();
        }

        //属性名
        tableField.setProperty(field.getName());

        //column name
        String columnName = getColumnName(globalConfig, field, tableField.isId());
        tableField.setColumn(columnName);

        //java type
        tableField.setJavaType(field.getType());

        // jdbcType
        JdbcType jdbcType = JdbcType.UNDEFINED;
        com.baomidou.mybatisplus.annotation.TableField tableFieldAnnotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        if (tableFieldAnnotation != null && tableFieldAnnotation.jdbcType() != JdbcType.UNDEFINED) {
            jdbcType = tableFieldAnnotation.jdbcType();
        }
        tableField.setJdbcType(jdbcType);

        return tableField;
    }

    private String getColumnName(GlobalConfig globalConfig, Field field, boolean isIdColumn) {
        MybatisPlusConfig mybatisPlusConfig = globalConfig.getMybatisPlusConfig();

        String columnName;
        if (isIdColumn) {
            //处理ID列
            TableId tableIdAnnotation = field.getAnnotation(TableId.class);
            if (StringUtils.isNotEmpty(tableIdAnnotation.value())) {
                columnName = tableIdAnnotation.value();
            } else {
                columnName = field.getName();
                columnName = StringUtils.transform(columnName, mybatisPlusConfig.getStyle());
            }
        } else {
            //处理非ID列
            com.baomidou.mybatisplus.annotation.TableField tableFieldAnnotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            if (tableFieldAnnotation != null && StringUtils.isNotEmpty(tableFieldAnnotation.value())) {
                columnName = tableFieldAnnotation.value();
            } else {
                columnName = field.getName();
                columnName = StringUtils.transform(columnName, mybatisPlusConfig.getStyle());
            }
        }
        return columnName;
    }
}
