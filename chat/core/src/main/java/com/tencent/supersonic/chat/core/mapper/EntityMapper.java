package com.tencent.supersonic.chat.core.mapper;


import com.tencent.supersonic.chat.api.pojo.ModelSchema;
import com.tencent.supersonic.chat.core.pojo.QueryContext;
import com.tencent.supersonic.chat.api.pojo.SchemaElement;
import com.tencent.supersonic.chat.api.pojo.SchemaElementMatch;
import com.tencent.supersonic.chat.api.pojo.SchemaElementType;
import com.tencent.supersonic.chat.api.pojo.SchemaMapInfo;
import com.tencent.supersonic.chat.api.pojo.SemanticSchema;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

/**
 * A mapper capable of converting the VALUE of entity dimension values into ID types.
 */
@Slf4j
public class EntityMapper extends BaseMapper {

    @Override
    public void doMap(QueryContext queryContext) {
        SchemaMapInfo schemaMapInfo = queryContext.getMapInfo();
        for (Long modelId : schemaMapInfo.getMatchedModels()) {
            List<SchemaElementMatch> schemaElementMatchList = schemaMapInfo.getMatchedElements(modelId);
            if (CollectionUtils.isEmpty(schemaElementMatchList)) {
                continue;
            }
            SchemaElement entity = getEntity(modelId, queryContext);
            if (entity == null || entity.getId() == null) {
                continue;
            }
            List<SchemaElementMatch> valueSchemaElements = schemaElementMatchList.stream()
                    .filter(schemaElementMatch ->
                            SchemaElementType.VALUE.equals(schemaElementMatch.getElement().getType()))
                    .collect(Collectors.toList());
            for (SchemaElementMatch schemaElementMatch : valueSchemaElements) {
                if (!entity.getId().equals(schemaElementMatch.getElement().getId())) {
                    continue;
                }
                if (!checkExistSameEntitySchemaElements(schemaElementMatch, schemaElementMatchList)) {
                    SchemaElementMatch entitySchemaElementMath = new SchemaElementMatch();
                    BeanUtils.copyProperties(schemaElementMatch, entitySchemaElementMath);
                    entitySchemaElementMath.setElement(entity);
                    schemaElementMatchList.add(entitySchemaElementMath);
                }
                schemaElementMatch.getElement().setType(SchemaElementType.ID);
            }
        }
    }

    private boolean checkExistSameEntitySchemaElements(SchemaElementMatch valueSchemaElementMatch,
            List<SchemaElementMatch> schemaElementMatchList) {
        List<SchemaElementMatch> entitySchemaElements = schemaElementMatchList.stream().filter(schemaElementMatch ->
                        SchemaElementType.ENTITY.equals(schemaElementMatch.getElement().getType()))
                .collect(Collectors.toList());
        for (SchemaElementMatch schemaElementMatch : entitySchemaElements) {
            if (schemaElementMatch.getElement().getId().equals(valueSchemaElementMatch.getElement().getId())) {
                return true;
            }
        }
        return false;
    }

    private SchemaElement getEntity(Long modelId, QueryContext queryContext) {
        SemanticSchema semanticSchema = queryContext.getSemanticSchema();
        ModelSchema modelSchema = semanticSchema.getModelSchemaMap().get(modelId);
        if (modelSchema != null && modelSchema.getEntity() != null) {
            return modelSchema.getEntity();
        }
        return null;
    }
}