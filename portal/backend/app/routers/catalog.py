"""GET /api/catalog: proxy cacheado del catalog del coordinator."""
from fastapi import APIRouter, Depends

from app.schemas import CatalogResponse
from app.services.catalog_service import CatalogService, get_catalog_service

router = APIRouter()


@router.get("/catalog", response_model=CatalogResponse)
async def get_catalog(
    refresh: bool = False,
    svc: CatalogService = Depends(get_catalog_service),
) -> CatalogResponse:
    return await svc.get(force_refresh=refresh)
