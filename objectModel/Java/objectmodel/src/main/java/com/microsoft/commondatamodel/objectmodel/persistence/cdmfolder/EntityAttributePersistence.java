// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.commondatamodel.objectmodel.persistence.cdmfolder;

import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.commondatamodel.objectmodel.cdm.CdmCorpusContext;
import com.microsoft.commondatamodel.objectmodel.cdm.CdmEntityAttributeDefinition;
import com.microsoft.commondatamodel.objectmodel.cdm.CdmEntityReference;
import com.microsoft.commondatamodel.objectmodel.cdm.projections.CardinalitySettings;
import com.microsoft.commondatamodel.objectmodel.cdm.projections.CdmProjection;
import com.microsoft.commondatamodel.objectmodel.enums.CdmObjectType;
import com.microsoft.commondatamodel.objectmodel.enums.CdmPropertyName;
import com.microsoft.commondatamodel.objectmodel.persistence.cdmfolder.projections.ProjectionPersistence;
import com.microsoft.commondatamodel.objectmodel.persistence.cdmfolder.types.EntityAttribute;
import com.microsoft.commondatamodel.objectmodel.utilities.CopyOptions;
import com.microsoft.commondatamodel.objectmodel.utilities.JMapper;
import com.microsoft.commondatamodel.objectmodel.utilities.ResolveOptions;
import com.microsoft.commondatamodel.objectmodel.utilities.StringUtils;
import com.microsoft.commondatamodel.objectmodel.utilities.logger.Logger;

public class EntityAttributePersistence {

  public static CdmEntityAttributeDefinition fromData(final CdmCorpusContext ctx, final JsonNode obj) {
    final CdmEntityAttributeDefinition entityAttribute =
        ctx.getCorpus().makeObject(CdmObjectType.EntityAttributeDef,
            obj.get("name").asText());

    if (obj.get("cardinality") != null) {
      String minCardinality = null;
      if (obj.get("cardinality").get("minimum") != null) {
        minCardinality = obj.get("cardinality").get("minimum").asText();
      }

      String maxCardinality = null;
      if (obj.get("cardinality").get("maximum") != null) {
        maxCardinality = obj.get("cardinality").get("maximum").asText();
      }

      if (StringUtils.isNullOrTrimEmpty(minCardinality) || StringUtils.isNullOrTrimEmpty(maxCardinality)) {
        Logger.error(EntityAttributePersistence.class.getSimpleName(), ctx, "Both minimum and maximum are required for the Cardinality property.", "fromData");
      }

      if (!CardinalitySettings.isMinimumValid(minCardinality)) {
        Logger.error(EntityAttributePersistence.class.getSimpleName(), ctx, Logger.format("Invalid minimum cardinality {0}.", minCardinality), "fromData");
      }

      if (!CardinalitySettings.isMaximumValid(maxCardinality)) {
        Logger.error(EntityAttributePersistence.class.getSimpleName(), ctx, Logger.format("Invalid maximum cardinality {0}.", maxCardinality), "fromData");
      }

      if (!StringUtils.isNullOrTrimEmpty(minCardinality) &&
          !StringUtils.isNullOrTrimEmpty(maxCardinality) &&
          CardinalitySettings.isMinimumValid(minCardinality) &&
          CardinalitySettings.isMaximumValid(maxCardinality)) {
        entityAttribute.setCardinality(new CardinalitySettings(entityAttribute));
        entityAttribute.getCardinality().setMinimum(minCardinality);
        entityAttribute.getCardinality().setMaximum(maxCardinality);
      }
    }

    if (obj.has("explanation")) {
      entityAttribute.setExplanation(obj.get("explanation").asText());
    }

    entityAttribute.updateDescription(Utils.propertyFromDataToString(obj.get("description")));
    entityAttribute.updateDisplayName(Utils.propertyFromDataToString(obj.get("displayName")));

    if (obj.has("isPolymorphicSource")) {
      entityAttribute.setIsPolymorphicSource(obj.get("isPolymorphicSource").asBoolean());
    }

    boolean isProjection = obj.get("entity") != null && !(obj.get("entity").isValueNode()) && obj.get("entity").get("source") != null;

    if (isProjection) {
      CdmProjection projection = ProjectionPersistence.fromData(ctx, obj.get("entity"));
      projection.setOwner(entityAttribute);

      CdmEntityReference inlineEntityRef = ctx.getCorpus().makeObject(CdmObjectType.EntityRef, null);
      inlineEntityRef.setExplicitReference(projection);
      entityAttribute.setEntity(inlineEntityRef);
    } else {
      entityAttribute.setEntity(EntityReferencePersistence.fromData(ctx, obj.get("entity")));
    }

    entityAttribute.setPurpose(PurposeReferencePersistence.fromData(ctx, obj.get("purpose")));
    Utils.addListToCdmCollection(entityAttribute.getAppliedTraits(),
        Utils.createTraitReferenceList(ctx, obj.get("appliedTraits")));
    // Ignore resolution guidance if the entity is a projection
    if (obj.get("resolutionGuidance") != null && isProjection) {
      Logger.error(
          EntityAttributePersistence.class.getSimpleName(),
          ctx,
          Logger.format("The EntityAttribute {0} is projection based. Resolution guidance is not supported with a projection.", entityAttribute.getName())
      );
    } else {
      entityAttribute.setResolutionGuidance(AttributeResolutionGuidancePersistence.fromData(ctx, obj.get("resolutionGuidance")));
    }
    return entityAttribute;
  }

  public static EntityAttribute toData(final CdmEntityAttributeDefinition instance, final ResolveOptions resOpt,
                                       final CopyOptions options) {
    final EntityAttribute result = new EntityAttribute();

    result.setExplanation(instance.getExplanation());
    result.setDescription((String)instance.fetchProperty(CdmPropertyName.DESCRIPTION));
    result.setDisplayName((String)instance.fetchProperty(CdmPropertyName.DISPLAY_NAME));
    result.setName(instance.getName());
    result.setIsPolymorphicSource(instance.getIsPolymorphicSource());
    result.setEntity(Utils.jsonForm(instance.getEntity(), resOpt, options));
    result.setPurpose(Utils.jsonForm(instance.getPurpose(), resOpt, options));
    result.setAppliedTraits(Utils.listCopyDataAsArrayNode(
      instance.getAppliedTraits().getAllItems()
          .stream()
          .filter(trait -> !trait.isFromProperty())
          .collect(Collectors.toList()),
      resOpt,
      options));
    result.setResolutionGuidance(
            JMapper.MAP.valueToTree(Utils.jsonForm(instance.getResolutionGuidance(), resOpt, options)));
    return result;
  }
}
