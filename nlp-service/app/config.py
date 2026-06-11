import os
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    app_name: str = "Surgery Extract NLP Service"
    app_version: str = "1.0.0"
    debug: bool = True

    host: str = "0.0.0.0"
    port: int = 8000

    upload_dir: str = "./data/uploads"
    temp_dir: str = "./data/temp"
    output_dir: str = "./data/output"

    tesseract_cmd: str = os.getenv("TESSERACT_CMD", "tesseract")
    tesseract_lang: str = "chi_sim+eng"

    model_dir: str = "./models"
    bert_model_name: str = "bert-base-chinese"
    device: str = "cpu"
    use_gpu: bool = False

    max_text_length: int = 10000
    batch_size: int = 16

    regex_rules_file: str = "./config/regex_rules.json"
    dict_dir: str = "./config/dicts"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    return Settings()
