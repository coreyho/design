// SaaS数据权限规则配置页面的JavaScript逻辑
document.addEventListener('DOMContentLoaded', function() {
    // 获取DOM元素
    const expressionOutput = document.getElementById('expressionOutput');
    const conditionRowsContainer = document.querySelector('.expression-builder');
    const saveBtn = document.getElementById('saveBtn');
    const previewBtn = document.getElementById('previewBtn');
    const resetBtn = document.getElementById('resetBtn');
    const rulesTableBody = document.querySelector('#rulesTable tbody');
    
    // 添加条件行
    function addConditionRow() {
        const newRow = document.createElement('div');
        newRow.className = 'condition-row';
        newRow.innerHTML = `
            <select class="condition-type">
                <option value="field">字段条件</option>
                <option value="relation">关系条件</option>
                <option value="function">函数条件</option>
            </select>
            
            <select class="field-name">
                <option value="">选择字段</option>
                <option value="department_id">部门ID</option>
                <option value="creator_id">创建者ID</option>
                <option value="region">区域</option>
                <option value="status">状态</option>
            </select>
            
            <select class="operator">
                <option value="eq">等于</option>
                <option value="ne">不等于</option>
                <option value="gt">大于</option>
                <option value="lt">小于</option>
                <option value="in">在列表中</option>
                <option value="like">模糊匹配</option>
            </select>
            
            <input type="text" class="value-input" placeholder="输入值">
            
            <button class="add-condition-btn">+</button>
            <button class="remove-condition-btn">-</button>
        `;
        
        // 添加事件监听器到新行
        attachEventListenersToRow(newRow);
        
        // 将新行插入到容器中（在最后一个按钮之前）
        const addButton = document.querySelector('.add-condition-btn');
        if (addButton) {
            addButton.parentNode.parentNode.insertBefore(newRow, addButton.parentNode.nextSibling);
        } else {
            conditionRowsContainer.appendChild(newRow);
        }
        
        // 更新表达式预览
        updateExpressionPreview();
    }
    
    // 移除条件行
    function removeConditionRow(button) {
        const row = button.closest('.condition-row');
        if (conditionRowsContainer.querySelectorAll('.condition-row').length > 1) {
            row.remove();
            updateExpressionPreview();
        } else {
            alert('至少需要保留一行条件');
        }
    }
    
    // 更新表达式预览
    function updateExpressionPreview() {
        const rows = document.querySelectorAll('.condition-row');
        const conditions = [];
        
        rows.forEach(row => {
            const field = row.querySelector('.field-name').value;
            const operator = row.querySelector('.operator').value;
            const value = row.querySelector('.value-input').value;
            
            if (field && operator) {
                let opSymbol = getOperatorSymbol(operator);
                let condition = `${field} ${opSymbol} ?`;
                
                if (value) {
                    condition = condition.replace('?', `"${value}"`);
                }
                
                conditions.push(condition);
            }
        });
        
        if (conditions.length === 0) {
            expressionOutput.textContent = '';
        } else if (conditions.length === 1) {
            expressionOutput.textContent = `(${conditions[0]})`;
        } else {
            expressionOutput.textContent = `(${conditions.join(' AND ')})`;
        }
    }
    
    // 获取操作符符号
    function getOperatorSymbol(operator) {
        switch(operator) {
            case 'eq': return '=';
            case 'ne': return '!=';
            case 'gt': return '>';
            case 'lt': return '<';
            case 'in': return 'IN';
            case 'like': return 'LIKE';
            default: return '=';
        }
    }
    
    // 保存规则
    function saveRule() {
        const policyName = document.getElementById('policyName').value;
        const resourceType = document.getElementById('resourceType').value;
        const role = document.getElementById('role').value;
        const operation = document.getElementById('operation').value;
        const expression = expressionOutput.textContent;
        
        if (!policyName || !resourceType || !role || !expression) {
            alert('请填写所有必填字段');
            return;
        }
        
        // 创建规则对象
        const rule = {
            policyName,
            resourceType,
            role,
            operation,
            expression,
            createdAt: new Date().toLocaleString()
        };
        
        // 添加到表格
        addRuleToTable(rule);
        
        // 清空表单
        document.getElementById('policyName').value = '';
        document.getElementById('resourceType').value = '';
        document.getElementById('role').value = '';
        document.getElementById('operation').value = 'read';
        expressionOutput.textContent = '(department_id = ?)';
        
        // 重置条件行
        const rows = document.querySelectorAll('.condition-row');
        if (rows.length > 1) {
            rows.forEach((row, index) => {
                if (index > 0) {
                    row.remove();
                } else {
                    row.querySelector('.field-name').value = '';
                    row.querySelector('.operator').value = 'eq';
                    row.querySelector('.value-input').value = '';
                }
            });
        } else {
            document.querySelector('.field-name').value = '';
            document.querySelector('.operator').value = 'eq';
            document.querySelector('.value-input').value = '';
        }
        
        alert('规则保存成功！');
    }
    
    // 添加规则到表格
    function addRuleToTable(rule) {
        const row = document.createElement('tr');
        
        row.innerHTML = `
            <td>${rule.policyName}</td>
            <td>${rule.resourceType}</td>
            <td>${rule.role}</td>
            <td>${rule.operation}</td>
            <td>${rule.expression}</td>
            <td>
                <button class="btn-edit" onclick="editRule(this)">编辑</button>
                <button class="btn-delete" onclick="deleteRule(this)">删除</button>
            </td>
        `;
        
        rulesTableBody.appendChild(row);
    }
    
    // 预览SQL
    function previewSQL() {
        const expression = expressionOutput.textContent;
        if (!expression) {
            alert('请先构建权限表达式');
            return;
        }
        
        // 模拟SQL预览
        const sqlPreview = `SELECT * FROM resources WHERE ${expression}`;
        alert(`SQL预览:\n${sqlPreview}`);
    }
    
    // 重置表单
    function resetForm() {
        if (confirm('确定要重置所有内容吗？')) {
            document.getElementById('policyName').value = '';
            document.getElementById('resourceType').value = '';
            document.getElementById('role').value = '';
            document.getElementById('operation').value = 'read';
            expressionOutput.textContent = '(department_id = ?)';
            
            // 重置条件行
            const rows = document.querySelectorAll('.condition-row');
            if (rows.length > 1) {
                rows.forEach((row, index) => {
                    if (index > 0) {
                        row.remove();
                    } else {
                        row.querySelector('.field-name').value = '';
                        row.querySelector('.operator').value = 'eq';
                        row.querySelector('.value-input').value = '';
                    }
                });
            } else {
                document.querySelector('.field-name').value = '';
                document.querySelector('.operator').value = 'eq';
                document.querySelector('.value-input').value = '';
            }
        }
    }
    
    // 给行添加事件监听器
    function attachEventListenersToRow(row) {
        const addBtn = row.querySelector('.add-condition-btn');
        const removeBtn = row.querySelector('.remove-condition-btn');
        const selects = row.querySelectorAll('select');
        const inputs = row.querySelectorAll('input');
        
        addBtn.addEventListener('click', addConditionRow);
        removeBtn.addEventListener('click', () => removeConditionRow(removeBtn));
        
        selects.forEach(select => {
            select.addEventListener('change', updateExpressionPreview);
        });
        
        inputs.forEach(input => {
            input.addEventListener('input', updateExpressionPreview);
        });
    }
    
    // 给现有行添加事件监听器
    document.querySelectorAll('.condition-row').forEach(row => {
        attachEventListenersToRow(row);
    });
    
    // 全局函数供HTML调用
    window.editRule = function(button) {
        const row = button.closest('tr');
        const cells = row.cells;
        
        document.getElementById('policyName').value = cells[0].textContent;
        document.getElementById('resourceType').value = cells[1].textContent;
        document.getElementById('role').value = cells[2].textContent;
        document.getElementById('operation').value = cells[3].textContent;
        expressionOutput.textContent = cells[4].textContent;
        
        // 暂时没有解析表达式回填到条件行的功能，可以后续扩展
        alert('编辑功能：当前仅演示界面交互，实际编辑功能需后端支持');
    };
    
    window.deleteRule = function(button) {
        if (confirm('确定要删除这条规则吗？')) {
            const row = button.closest('tr');
            row.remove();
        }
    };
    
    // 绑定主要按钮事件
    saveBtn.addEventListener('click', saveRule);
    previewBtn.addEventListener('click', previewSQL);
    resetBtn.addEventListener('click', resetForm);
});