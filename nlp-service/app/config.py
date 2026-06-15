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
    bert_local_path: str = os.getenv("BERT_LOCAL_PATH", "./models/bert-base-chinese")
    device: str = "cpu"
    use_gpu: bool = False
    offline_mode: bool = os.getenv("OFFLINE_MODE", "false").lower() == "true"

    max_text_length: int = 10000
    batch_size: int = 16

    regex_rules_file: str = "./config/regex_rules.json"
    dict_dir: str = "./config/dicts"

    db_host: str = os.getenv("DB_HOST", "localhost")
    db_port: int = int(os.getenv("DB_PORT", "3306"))
    db_user: str = os.getenv("DB_USER", "root")
    db_password: str = os.getenv("DB_PASSWORD", "")
    db_name: str = os.getenv("DB_NAME", "surg_extract_nlp")

    train_script_path: str = os.getenv("TRAIN_SCRIPT_PATH", "./scripts/incremental_finetune.py")
    train_model_dir: str = os.getenv("TRAIN_MODEL_DIR", "./models/surgery-ner")
    train_output_dir: str = os.getenv("TRAIN_OUTPUT_DIR", "./models/surgery-ner-finetuned")
    train_base_data: str = os.getenv("TRAIN_BASE_DATA", "")

    weekly_train_enabled: bool = os.getenv("WEEKLY_TRAIN_ENABLED", "false").lower() == "true"
    weekly_train_day: int = int(os.getenv("WEEKLY_TRAIN_DAY", "1"))
    weekly_train_hour: int = int(os.getenv("WEEKLY_TRAIN_HOUR", "2"))

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    return Settings()
