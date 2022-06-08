import { IPanelProps } from "@blueprintjs/core";
import {
  PropertyPaneConfig,
  PropertyPaneControlConfig,
  PropertyPaneSectionConfig,
} from "constants/PropertyControlConstants";
import { WidgetType } from "constants/WidgetConstants";
import React from "react";
import WidgetFactory from "utils/WidgetFactory";
import PropertyControl from "./PropertyControl";
import PropertySection from "./PropertySection";
import { EditorTheme } from "components/editorComponents/CodeEditor/EditorConfig";
import Boxed from "../GuidedTour/Boxed";
import { GUIDED_TOUR_STEPS } from "../GuidedTour/constants";

export enum PropertyPaneGroup {
  CONTENT,
  STYLE,
}

export type PropertyControlsGeneratorProps = {
  id: string;
  group?: PropertyPaneGroup;
  type: WidgetType;
  panel: IPanelProps;
  theme: EditorTheme;
};

export const generatePropertyControl = (
  propertyPaneConfig: readonly PropertyPaneConfig[],
  props: PropertyControlsGeneratorProps,
) => {
  if (!propertyPaneConfig) return null;
  return propertyPaneConfig.map((config: PropertyPaneConfig) => {
    if ((config as PropertyPaneSectionConfig).sectionName) {
      const sectionConfig: PropertyPaneSectionConfig = config as PropertyPaneSectionConfig;
      return (
        <Boxed
          key={config.id + props.id}
          show={
            sectionConfig.sectionName !== "General" &&
            props.type === "TABLE_WIDGET"
          }
          step={GUIDED_TOUR_STEPS.TABLE_WIDGET_BINDING}
        >
          <PropertySection
            hidden={sectionConfig.hidden}
            id={config.id || sectionConfig.sectionName}
            isDefaultOpen={sectionConfig.isDefaultOpen}
            key={config.id + props.id}
            name={sectionConfig.sectionName}
            propertyPath={sectionConfig.propertySectionPath}
          >
            {config.children && generatePropertyControl(config.children, props)}
          </PropertySection>
        </Boxed>
      );
    } else if ((config as PropertyPaneControlConfig).controlType) {
      return (
        <Boxed
          key={config.id + props.id}
          show={
            (config as PropertyPaneControlConfig).propertyName !==
              "tableData" && props.type === "TABLE_WIDGET"
          }
          step={GUIDED_TOUR_STEPS.TABLE_WIDGET_BINDING}
        >
          <PropertyControl
            key={config.id + props.id}
            {...(config as PropertyPaneControlConfig)}
            panel={props.panel}
            theme={props.theme}
          />
        </Boxed>
      );
    }
    throw Error("Unknown configuration provided: " + props.type);
  });
};

export function PropertyControlsGenerator(
  props: PropertyControlsGeneratorProps,
) {
  let config;
  switch (props.group) {
    case PropertyPaneGroup.CONTENT:
      config = WidgetFactory.getWidgetPropertyPaneContentConfig(props.type);
      break;
    case PropertyPaneGroup.STYLE:
      config = WidgetFactory.getWidgetPropertyPaneStyleConfig(props.type);
      break;
    default:
      config = WidgetFactory.getWidgetPropertyPaneConfig(props.type);
  }
  return (
    <div className="px-3">
      {generatePropertyControl(config as readonly PropertyPaneConfig[], props)}
    </div>
  );
}

export default PropertyControlsGenerator;
