"""OffensiveRed backend.

A thin FastAPI service that lets the RedSecAI JavaFX GUI drive the upstream
`Decepticon <https://github.com/PurpleAILAB/Decepticon>`_ autonomous red-team
framework. The backend does not implement any scanning logic of its own -- see
:mod:`backend.runner` for the in-process adapter around the ``decepticon``
library's agent factories.
"""

__version__ = "1.0.0"
