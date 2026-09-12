"""Mapbox streets-v12 베이스로 임장 지도 커스텀 스타일을 생성합니다.

- 라벨: 영문 우선(coalesce(name_en, name)) -> 로컬(한글) 우선(coalesce(name, name_en))
- 건물: 평면 fill -> fill-extrusion (흰색/크림톤 3D)

실행: frontend/ 에서 `python scripts/build_field_map_style.py`
frontend/.env.local 의 EXPO_PUBLIC_MAPBOX_TOKEN 으로 원본 스타일을 매번 새로 받아오므로,
Mapbox 가 streets-v12 를 업데이트하면 이 스크립트를 다시 돌려서 최신화하면 됩니다.
"""
import copy
import json
import os
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FRONTEND_DIR = os.path.dirname(SCRIPT_DIR)
OUT = os.path.join(FRONTEND_DIR, "src", "assets", "mapStyles", "fieldMapStyle.json")
SESSION_OUT = os.path.join(
    FRONTEND_DIR, "src", "assets", "mapStyles", "fieldSessionMapStyle.json"
)
STANDARD_SESSION_OUT = os.path.join(
    FRONTEND_DIR, "src", "assets", "mapStyles", "fieldStandardSessionMapStyle.json"
)


def read_mapbox_token() -> str:
    env_path = os.path.join(FRONTEND_DIR, ".env.local")
    with open(env_path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("EXPO_PUBLIC_MAPBOX_TOKEN="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise RuntimeError(f"EXPO_PUBLIC_MAPBOX_TOKEN 을 {env_path} 에서 못 찾음")


def fetch_base_style() -> dict:
    token = read_mapbox_token()
    url = f"https://api.mapbox.com/styles/v1/mapbox/streets-v12?access_token={token}"
    with urllib.request.urlopen(url) as resp:
        return json.load(resp)

# 일반 지도(지도 탭)에서 아예 빼는 레이어.
#
# 이 화면의 주인공은 아파트 가격 마커입니다. 도로명·건물번호가 마커와 같은 자리를
# 두고 경쟁하면 정작 봐야 할 가격이 안 읽히고, Mapbox 가 겹침을 피하려고 마커를
# 통째로 숨기기도 합니다. 임장 지도에서 hidden_field_label_ids 로 빼는 것과 같은
# 이유이고, 앞의 여섯 개는 목록도 동일합니다.
#
# poi-label / transit-label 은 남깁니다. 두 화면 모두 시설 필터가 이 레이어의
# filter 를 런타임에 덮어쓰는 방식으로 동작하기 때문입니다.
BROWSE_MAP_HIDDEN_LAYERS = {
    # 임장 지도와 동일한 여섯 개
    "road-label",
    "road-intersection",
    "road-number-shield",
    "road-exit-shield",
    "path-pedestrian-label",
    "ferry-aerialway-label",
    # 일방통행 화살표·횡단보도·건널목. 확대하면 도로를 뒤덮습니다.
    "road-oneway-arrow-blue",
    "road-oneway-arrow-white",
    "bridge-oneway-arrow-blue",
    "bridge-oneway-arrow-white",
    "tunnel-oneway-arrow-blue",
    "tunnel-oneway-arrow-white",
    "crosswalks",
    "level-crossing",
    # 건물 번호·출입구
    "building-entrance",
    "building-number-label",
    "block-number-label",
    # 골프홀·공항. 서울 시내 아파트 지도에서는 나올 일이 없습니다.
    "golf-hole-label",
    "airport-label",
    # 국가·대륙·광역. 카메라가 서울로 묶여 있어 화면에 들어오지 않습니다.
    "state-label",
    "country-label",
    "continent-label",
}


def declutter_browse_map(style: dict) -> dict:
    """일반 지도용으로 길찾기 라벨을 걷어낸 사본. 남기는 건 지명·지하철역·물길입니다."""
    browse_style = copy.deepcopy(style)
    browse_style["layers"] = [
        layer
        for layer in browse_style["layers"]
        if layer.get("id") not in BROWSE_MAP_HIDDEN_LAYERS
    ]
    browse_style["name"] = "ssabangpalbang-browse-map"

    return browse_style


OLD_PATTERN = ["coalesce", ["get", "name_en"], ["get", "name"]]
NEW_PATTERN = ["coalesce", ["get", "name"], ["get", "name_en"]]

BUILDING_COLOR = "#FFFFFF"  # 임장 화면의 건물을 레퍼런스처럼 밝은 백색 계열로 유지합니다.
# 어둡게 음영 처리해서, 지붕(순색)보다 옆면이 항상 더 어둡게 보입니다(정상 렌더링
# 특성). 그래서 실제 결과가 크림색이 아니라 흰색에 가깝게 보이도록 아주 밝게 잡습니다.


LAND_COLOR = "#F8FAF9"
WATER_COLOR = "#DFF4F5"
GREENSPACE_COLOR = "#DDF5E5"
ROAD_COLOR = "#FFFFFF"
ROAD_EDGE_COLOR = "#D9E0DE"


def swap_name_priority(node):
    """중첩된 리스트/딕트를 재귀적으로 훑어 OLD_PATTERN을 NEW_PATTERN으로 치환."""
    if node == OLD_PATTERN:
        return json.loads(json.dumps(NEW_PATTERN))
    if isinstance(node, list):
        return [swap_name_priority(x) for x in node]
    if isinstance(node, dict):
        return {k: swap_name_priority(v) for k, v in node.items()}
    return node


def main():
    style = fetch_base_style()

    changed_labels = 0
    for layer in style["layers"]:
        layout = layer.get("layout")
        if not layout or "text-field" not in layout:
            continue
        before = layout["text-field"]
        after = swap_name_priority(before)
        if after != before:
            layout["text-field"] = after
            changed_labels += 1

    # 건물: fill -> fill-extrusion (흰색/크림, 실제 높이 데이터를 축소해서 사용)
    #
    # 실제 층수 그대로 압출하면 아파트 단지가 너무 높이 솟아, 카메라가 내려다보는
    # 각도에서 경로선(2D 라인)을 가려야 할 부분까지 건물이 위로 넘어가 버립니다.
    # Mapbox 는 2D 라인/심볼 레이어를 3D 건물과 깊이 비교 없이 항상 그 위에
    # 그리므로(엔진 자체 한계, 레이어 순서로 해결 불가), 건물을 낮추면 경로선이
    # 건물을 뚫고 떠 보이는 구간이 줄어듭니다. 높이를 45% 로 줄이고 60m 로 캡합니다.
    # 한글 라벨 표현식은 유지하고 지도 바탕색만 임장 화면 전용 저채도 팔레트로 바꿉니다.
    for layer in style["layers"]:
        layer_id = layer.get("id", "")
        component = layer.get("metadata", {}).get("mapbox:featureComponent", "")
        paint = layer.get("paint", {})

        if layer_id == "land" and "background-color" in paint:
            paint["background-color"] = LAND_COLOR

        if layer_id in {"landcover", "national-park"} and "fill-color" in paint:
            paint["fill-color"] = GREENSPACE_COLOR

        if component == "land-and-water":
            if layer_id == "water" and "fill-color" in paint:
                paint["fill-color"] = WATER_COLOR
            elif "water" in layer_id and "line-color" in paint:
                paint["line-color"] = WATER_COLOR

        if component in {"road-network", "walking-cycling"} and "line-color" in paint:
            paint["line-color"] = (
                ROAD_EDGE_COLOR
                if any(token in layer_id for token in ("case", "shadow", "outline"))
                else ROAD_COLOR
            )

    HEIGHT_SCALE = 0.62
    HEIGHT_CAP_M = 90
    building_changed = False
    for layer in style["layers"]:
        if layer["id"] == "building":
            layer["type"] = "fill-extrusion"
            layer["paint"] = {
                "fill-extrusion-color": BUILDING_COLOR,
                "fill-extrusion-height": [
                    "min",
                    ["*", ["get", "height"], HEIGHT_SCALE],
                    HEIGHT_CAP_M,
                ],
                "fill-extrusion-base": ["*", ["get", "min_height"], HEIGHT_SCALE],
                "fill-extrusion-opacity": [
                    "interpolate", ["linear"], ["zoom"],
                    15, 0,
                    16, 0.95,
                ],
                # 기본 true 면 벽 아래쪽이 위쪽보다 더 어둡게 그라디언트 처리됩니다.
                # 꺼서 벽 전체를 한 가지 톤으로 균일하게 만듭니다(레퍼런스의 납작한
                # 일러스트 느낌에 더 가까움).
                "fill-extrusion-vertical-gradient": True,
            }
            building_changed = True
            break

    # 조명: 방향성 조명은 각도에 따라 어떤 벽은 밝고 어떤 벽은 어두워져 예측하기
    # 어렵습니다(intensity 를 올렸더니 오히려 우리가 보는 벽이 더 어두워짐). 대신
    # intensity 를 아주 낮춰 거의 무방향(균일 대비) 조명으로 만들어, 벽·지붕이
    # 카메라 방향과 무관하게 고르게 밝은 색을 유지하게 합니다.
    style["light"] = {
        "anchor": "map",
        "color": "#ffffff",
        "intensity": 0.32,
        "position": [1.15, 210, 45],
    }

    style["fog"] = {
        "range": [1, 12],
        "color": "#F8FAF9",
        "high-color": "#EAF4F2",
        "space-color": "#F8FAF9",
        "horizon-blend": 0.06,
        "star-intensity": 0,
    }

    style["name"] = "ssabangpalbang-field-map"

    # 일반 지도는 기존 명암을 유지하고, 임장 화면만 반투명 건물을 사용합니다.
    # 가까이 확대할수록 조금 더 선명해지되 경로를 완전히 가리지 않는 범위입니다.
    session_style = copy.deepcopy(style)
    session_style["name"] = "ssabangpalbang-field-session-map"
    building_layer = None
    for layer in session_style["layers"]:
        if layer["id"] == "building":
            layer["paint"]["fill-extrusion-opacity"] = [
                "interpolate", ["linear"], ["zoom"],
                15, 0,
                15.5, 0.84,
                17, 0.88,
                19, 0.92,
            ]
            building_layer = layer
            break

    # Classic 스타일은 원래 building 이 road보다 앞에 있습니다. 임장 화면에서는
    # building 을 마지막 도로 면/선 뒤로 옮겨 road → route → building 순서를 만들 수
    # 있게 합니다. 런타임 경로는 belowLayerID="building"으로 들어갑니다.
    if building_layer is not None:
        session_style["layers"].remove(building_layer)
        road_layer_indices = [
            index
            for index, layer in enumerate(session_style["layers"])
            if layer.get("metadata", {}).get("mapbox:featureComponent")
            in {"road-network", "walking-cycling"}
            and layer.get("type") in {"line", "fill"}
        ]
        building_index = (
            max(road_layer_indices) + 1
            if road_layer_indices
            else len(session_style["layers"])
        )
        session_style["layers"].insert(building_index, building_layer)

    # Mapbox Standard가 3D 건물·나무·조명을 담당하고, Streets의 한글 우선
    # 심볼 레이어만 top 슬롯에 얹습니다. 중복 표기를 막기 위해 Standard의
    # 기본 라벨은 숨깁니다.
    # 임장 지도에서는 길찾기 경로와 현장 오브젝트가 주인공이므로 도로명·도로 번호는
    # 표시하지 않습니다. Standard 쪽 showRoadLabels만 끄면 아래에 별도로 얹는
    # Streets 한글 레이어의 "역삼로", "삼전로" 등이 다시 나타나므로 여기서도
    # 도로 계열 심볼을 제외해야 합니다.
    hidden_field_label_ids = {
        "road-label",
        "road-intersection",
        "road-number-shield",
        "road-exit-shield",
        "path-pedestrian-label",
        "ferry-aerialway-label",
    }

    korean_label_layers = []
    for source_layer in style["layers"]:
        if source_layer.get("type") != "symbol" or "text-field" not in source_layer.get("layout", {}):
            continue
        if source_layer.get("id") in hidden_field_label_ids:
            continue

        label_layer = copy.deepcopy(source_layer)
        label_layer["slot"] = "top"
        if label_layer.get("source") == "composite":
            label_layer["source"] = "korean-labels"
        korean_label_layers.append(label_layer)

    standard_session_style = {
        "version": 8,
        "name": "ssabangpalbang-field-standard-session-map",
        "imports": [
            {
                "id": "basemap",
                "url": "mapbox://styles/mapbox/standard",
                "config": {
                    "lightPreset": "day",
                    "theme": "default",
                    "showPointOfInterestLabels": False,
                    "showTransitLabels": False,
                    "showPlaceLabels": False,
                    "showRoadLabels": False,
                    "showPedestrianRoads": True,
                    "show3dObjects": True,
                    "show3dBuildings": True,
                    "show3dTrees": True,
                    "show3dLandmarks": True,
                    "show3dFacades": True,
                    "colorLand": LAND_COLOR,
                    "colorWater": WATER_COLOR,
                    "colorGreenspaces": GREENSPACE_COLOR,
                    "colorRoads": ROAD_COLOR,
                    "colorBuildings": BUILDING_COLOR,
                },
            }
        ],
        "sprite": style["sprite"],
        "glyphs": style["glyphs"],
        "sources": {"korean-labels": copy.deepcopy(style["sources"]["composite"])},
        "layers": korean_label_layers,
    }

    # 임장 지도용 파생 스타일을 모두 만든 뒤에 걷어냅니다. 먼저 지우면 임장 지도의
    # 한글 라벨(korean_label_layers)까지 같이 사라집니다.
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(declutter_browse_map(style), f, ensure_ascii=False)

    with open(SESSION_OUT, "w", encoding="utf-8") as f:
        json.dump(session_style, f, ensure_ascii=False)

    with open(STANDARD_SESSION_OUT, "w", encoding="utf-8") as f:
        json.dump(standard_session_style, f, ensure_ascii=False)

    print(f"라벨 레이어 {changed_labels}개 name/name_en 순서 교체")
    print(f"건물 레이어 3D 압출 전환: {building_changed}")
    print(f"저장: {OUT}")
    print(f"임장 화면용 반투명 스타일 저장: {SESSION_OUT}")


if __name__ == "__main__":
    main()
