import { createSelector, createFeatureSelector } from '@ngrx/store';
import { State as AppState } from '@src/app/app-reducer';
import {
  State,
  FeatureCollection,
  UserArea,
  SelectableArea,
  Feature,
  NationalArea,
  AreaGroup,
  StatePath,
  Boundary
} from './area.interfaces';
import { getIn } from 'immutable';

export const selectAreaState = createFeatureSelector<AppState, State>('area');

export const selectSelectedArea = createSelector(
  selectAreaState,
  (area: State) => area.currentSelection
);

export const selectSelectedAreaData = createSelector(
  selectAreaState,
  selectSelectedArea,
  (area: State, selectedAreas) =>
      (selectedAreas ? selectedAreas.map(s_area => getIn(area, s_area, [])) : [])
);

export const selectNationalAreas = createSelector(selectAreaState, (state: State) => {
  const { area } = state;
  const areaTypes = state.areaTypes.filter(areaType => Object.keys(area).includes(areaType));
  return areaTypes
    .map(areaType => area[areaType])
    .map(nationalArea => ({
      ...nationalArea,
      groups: Object.values(nationalArea.groups).map(group => ({
        ...group,
        areas: Object.values(group.areas)
      }))
    }));
});

export const selectUserAreas = createSelector(selectAreaState, (state: State) =>
  Object.values(state.userArea)
);

export const selectOverlap = createSelector(selectAreaState, state => state.selectionOverlap);

export const selectBoundaries = createSelector(selectAreaState, state => state.boundaries);

export const selectCalibratedCalculationAreas = createSelector(
  selectAreaState,
  state => state.calibratedCalculationAreas
);

export const selectAll = createSelector(
  selectNationalAreas,
  selectUserAreas,
  (nationalAreas: NationalArea[], userArea: UserArea[]) => ({
    nationalAreas,
    userArea
  })
);

export const selectSelectedFeatureCollections = createSelector(
  selectNationalAreas,
  selectUserAreas,
  selectBoundaries,
  selectSelectedArea,
  (
    nationalAreas: NationalArea[],
    userAreas: UserArea[],
    boundaries: Boundary[],
    selected?: StatePath[]
  ): {
    collections: FeatureCollection[];
    boundary: FeatureCollection;
    selected?: StatePath[];
  } => {
    const nationalFeatures = getNationalAreaFeatures(nationalAreas);
    const userFeatures = getUserAreasFeatures(userAreas);
    return {
      collections: [...nationalFeatures, ...userFeatures],
      boundary: createBoundaryFeature(boundaries),
      selected
    };
  }
);

function getNationalAreaFeatures(nationalAreas: NationalArea[]): FeatureCollection[] {
  const visibleGroups = nationalAreas.reduce(
    (groups: AreaGroup[], nationalArea) => [
      ...groups,
      ...nationalArea.groups.filter(group => group.visible)
    ],
    []
  );
  const features = visibleGroups.reduce(
    (areas: Feature[], group) => [...areas, ...group.areas.map(area => area.feature)],
    []
  );
  return features.length > 0 ? createFeatureCollection(features) : [];
}

function getFeatures(areas: SelectableArea[]) {
  return areas.filter(area => area.visible === true).map(area => area.feature);
}

function getUserAreasFeatures(userAreas: UserArea[]) {
  const features = getFeatures(userAreas);
  return features.length > 0 ? createFeatureCollection(features) : [];
}

function createFeatureCollection(features: Feature[]): FeatureCollection[] {
  if (features.length === 0) {
    return [];
  }
  return [
    {
      type: 'FeatureCollection',
      crs: {
        type: 'name',
        properties: {
          name: 'urn:ogc:def:crs:OGC:1.3:CRS84'
        }
      },
      features
    }
  ];
}

function createBoundaryFeature(boundaries: Boundary[]): FeatureCollection {
  return {
    type: 'FeatureCollection',
    crs: {
      type: 'name',
      properties: {
        name: 'urn:ogc:def:crs:OGC:1.3:CRS84'
      }
    },
    features: boundaries.map(({ name, polygon: geometry }) => ({
      type: 'Feature',
      properties: {
        name,
        id: 'boundaryFeature' + name,
        displayName: name,
        statePath: []
      },
      geometry
    }))
  };
}
