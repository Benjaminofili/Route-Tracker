package com.example.ui.components

import com.google.android.gms.maps.model.MapStyleOptions

object MapStyles {

    /**
     * Dark/Aubergine styling JSON for Google Maps.
     * Provides high contrast for running paths with dark navy/slate background.
     */
    const val DARK_MAP_STYLE_JSON = """
    [
      {
        "elementType": "geometry",
        "stylers": [
          { "color": "#212a37" }
        ]
      },
      {
        "elementType": "labels.icon",
        "stylers": [
          { "visibility": "off" }
        ]
      },
      {
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#758a99" }
        ]
      },
      {
        "elementType": "labels.text.stroke",
        "stylers": [
          { "color": "#1b232e" }
        ]
      },
      {
        "featureType": "administrative",
        "elementType": "geometry",
        "stylers": [
          { "color": "#758a99" }
        ]
      },
      {
        "featureType": "administrative.country",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#9ca5b3" }
        ]
      },
      {
        "featureType": "administrative.land_parcel",
        "stylers": [
          { "visibility": "off" }
        ]
      },
      {
        "featureType": "administrative.locality",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#cdd6e4" }
        ]
      },
      {
        "featureType": "poi",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#6b8096" }
        ]
      },
      {
        "featureType": "poi.park",
        "elementType": "geometry",
        "stylers": [
          { "color": "#1b3329" }
        ]
      },
      {
        "featureType": "poi.park",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#63997e" }
        ]
      },
      {
        "featureType": "road",
        "elementType": "geometry.fill",
        "stylers": [
          { "color": "#2c3848" }
        ]
      },
      {
        "featureType": "road",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#8a9ba8" }
        ]
      },
      {
        "featureType": "road.arterial",
        "elementType": "geometry",
        "stylers": [
          { "color": "#38475a" }
        ]
      },
      {
        "featureType": "road.highway",
        "elementType": "geometry",
        "stylers": [
          { "color": "#475b73" }
        ]
      },
      {
        "featureType": "road.highway.controlled_access",
        "elementType": "geometry",
        "stylers": [
          { "color": "#4e637e" }
        ]
      },
      {
        "featureType": "road.local",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#617487" }
        ]
      },
      {
        "featureType": "transit",
        "elementType": "geometry",
        "stylers": [
          { "color": "#2f3948" }
        ]
      },
      {
        "featureType": "water",
        "elementType": "geometry",
        "stylers": [
          { "color": "#151d28" }
        ]
      },
      {
        "featureType": "water",
        "elementType": "labels.text.fill",
        "stylers": [
          { "color": "#4a5d73" }
        ]
      }
    ]
    """

    val darkStyleOptions: MapStyleOptions by lazy {
        MapStyleOptions(DARK_MAP_STYLE_JSON)
    }
}
