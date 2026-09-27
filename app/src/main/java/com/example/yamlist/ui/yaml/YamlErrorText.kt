package com.example.yamlist.ui.yaml

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.yamlist.R
import com.example.yamlist.data.file.yaml.YamlError
import com.example.yamlist.data.file.yaml.YamlErrorKind

/** Where the problem is: "Line 12" for syntax errors, otherwise the field or task path. */
@Composable
fun yamlErrorLocation(error: YamlError): String =
    error.line?.let { stringResource(R.string.yaml_err_line_fmt, it) } ?: error.locator

@Composable
fun yamlErrorMessage(error: YamlError): String =
    stringResource(error.kind.messageRes(), *error.args.toTypedArray())

@StringRes
private fun YamlErrorKind.messageRes(): Int = when (this) {
    YamlErrorKind.FILE_TOO_LARGE -> R.string.yaml_err_file_too_large
    YamlErrorKind.SYNTAX -> R.string.yaml_err_syntax
    YamlErrorKind.PARSE_FAILED -> R.string.yaml_err_parse_failed
    YamlErrorKind.ROOT_NOT_MAPPING -> R.string.yaml_err_root_not_mapping
    YamlErrorKind.SCHEMA_VERSION_MISSING -> R.string.yaml_err_schema_version_missing
    YamlErrorKind.SCHEMA_VERSION_UNSUPPORTED -> R.string.yaml_err_schema_version_unsupported
    YamlErrorKind.PROJECT_MISSING -> R.string.yaml_err_project_missing
    YamlErrorKind.PROJECT_TITLE_MISSING -> R.string.yaml_err_project_title_missing
    YamlErrorKind.DUPLICATE_UUID -> R.string.yaml_err_duplicate_uuid
    YamlErrorKind.TOO_MANY_TASKS -> R.string.yaml_err_too_many_tasks
    YamlErrorKind.TOO_DEEP -> R.string.yaml_err_too_deep
    YamlErrorKind.TASK_NOT_MAPPING -> R.string.yaml_err_task_not_mapping
    YamlErrorKind.TASK_TITLE_MISSING -> R.string.yaml_err_task_title_missing
    YamlErrorKind.INVALID_PROGRESS_TARGET -> R.string.yaml_err_invalid_progress_target
    YamlErrorKind.INVALID_COMMENT -> R.string.yaml_err_invalid_comment
    YamlErrorKind.ORDER_NOT_INTEGER -> R.string.yaml_err_order_not_integer
    YamlErrorKind.ORDER_NEGATIVE -> R.string.yaml_err_order_negative
    YamlErrorKind.INVALID_PROGRESS_MODE -> R.string.yaml_err_invalid_progress_mode
    YamlErrorKind.INVALID_STATUS -> R.string.yaml_err_invalid_status
    YamlErrorKind.WEIGHT_NOT_NUMBER -> R.string.yaml_err_weight_not_number
    YamlErrorKind.WEIGHT_OUT_OF_RANGE -> R.string.yaml_err_weight_out_of_range
    YamlErrorKind.INVALID_PLANNED_MONTH -> R.string.yaml_err_invalid_planned_month
    YamlErrorKind.INVALID_DATE -> R.string.yaml_err_invalid_date
    YamlErrorKind.INVALID_DATETIME -> R.string.yaml_err_invalid_datetime
}
