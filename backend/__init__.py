"""OffensiveRed backend.

A thin FastAPI service that lets the RedSecAI JavaFX GUI drive the upstream
`Decepticon <https://github.com/PurpleAILAB/Decepticon>`_ autonomous red-team
framework. The backend does not implement any scanning logic of its own -- see
:mod:`backend.runner` for the adapter around the ``decepticon-cli`` entry point.
"""

__version__ = "1.0.0"
